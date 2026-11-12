package com.tikit.payment.service;

import com.tikit.common.idempotency.IdempotencyHasher;
import com.tikit.common.idempotency.IdempotencyService;
import com.tikit.common.idempotency.IdempotencyStart;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.request.PaymentConfirmRequestDTO;
import com.tikit.payment.dto.response.PaymentAttemptResponseDTO;
import com.tikit.payment.dto.toss.TossConfirmRequestDTO;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private static final String TOSS_STATUS_DONE = "DONE";
    private static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";
    private static final String OPERATION_CONFIRM = "PAYMENT_CONFIRM";
    private static final String RESOURCE_PAYMENT_ATTEMPT = "PAYMENT_ATTEMPT";

    private final PaymentConfirmTxService paymentConfirmTxService;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    // 결제 승인: 멱등키 확인 -> 1차 트랜잭션 -> Toss 승인(트랜잭션 밖) -> 2차 트랜잭션 -> 현재 결과 조회
    // @Transactional 없음: Toss를 기다리는 동안 DB 잠금과 커넥션을 잡고 있지 않으려고
    public PaymentConfirmResult confirm(Long paymentAttemptId, Long memberId,
                                        PaymentConfirmRequestDTO request, String idempotencyKey) {
        // 0. 서버 멱등키: 같은 키·같은 내용으로 이미 처리된 요청이면 다시 처리하지 않고 현재 상태로 응답
        //    (같은 키가 처리 중이거나 다른 내용이면 IdempotencyException -> 409)
        IdempotencyStart idempotency = idempotencyService.start(
                memberId, OPERATION_CONFIRM, idempotencyKey, requestHash(paymentAttemptId, request));
        if (idempotency.replay()) {
            return toResult(paymentAttemptId, memberId);
        }

        try {
            // 1. 1차 트랜잭션 (검사 실패면 PaymentException이 그대로 Advice로 감)
            ConfirmationStart start = paymentConfirmTxService.startConfirmation(paymentAttemptId, memberId, request);

            // 2. 새로 시작한 승인만 Toss 호출 (이미 승인, 처리 중이면 Toss를 다시 부르지 않음)
            if (start.status() == ConfirmationStartStatus.STARTED) {
                approveWithToss(start, memberId);
            }
        } catch (RuntimeException e) {
            // 검사 실패, Toss 거절 등 -> 멱등 실패 기록 (같은 키로 다시 요청하면 다시 처리)
            idempotencyService.fail(idempotency.requestId());
            throw e;
        }

        // 3. 처리 끝 (승인 완료, 결과 확인 중 모두) -> 같은 키 재요청은 이 결제 시도의 현재 상태로 응답
        idempotencyService.succeed(idempotency.requestId(), RESOURCE_PAYMENT_ATTEMPT, paymentAttemptId);

        // 4. 어떤 경우든 마지막에 DB의 현재 상태로 응답
        return toResult(paymentAttemptId, memberId);
    }

    // Toss 승인 호출 후 결과별로 2차 트랜잭션 기록
    private void approveWithToss(ConfirmationStart start, Long memberId) {
        Long paymentAttemptId = start.paymentAttemptId();
        TossPaymentResponseDTO response;

        // 1. Toss 승인 요청 (값은 모두 1차에서 꺼낸 DB 값, 멱등키는 결제 시도에 저장된 키)
        try {
            response = tossPaymentClient.confirmPayment(
                    new TossConfirmRequestDTO(start.paymentKey(), start.orderId(), start.amount()),
                    start.pgIdempotencyKey()
            );
        } catch (TossApiException e) {
            // 이미 처리된 결제라는 거절은 실제로 돈이 나갔을 수 있으므로 실패로 단정하지 않음
            if (ALREADY_PROCESSED_PAYMENT.equals(e.getTossCode())) {
                log.warn("toss already processed payment: paymentAttemptId={}", paymentAttemptId);
                paymentConfirmTxService.recordReconciling(paymentAttemptId, memberId);
                return;
            }
            // Toss가 거절 -> 실패 기록 후 예외를 다시 던져 Advice가 422 PAYMENT_DECLINED
            paymentConfirmTxService.recordFailure(paymentAttemptId, memberId, e.getTossCode());
            throw e;
        } catch (TossResultUnknownException | TossUnavailableException e) {
            // 응답 못 받음, Toss 장애 -> 결과 확인 중 (202, 프론트가 조회 API로 재조회)
            log.warn("toss result uncertain: paymentAttemptId={}, reason={}", paymentAttemptId, e.getMessage());
            paymentConfirmTxService.recordReconciling(paymentAttemptId, memberId);
            return;
        }

        // 2. 승인 완료가 아닌 응답이면 직접 확정하지 않고 결과 확인 중으로
        if (!TOSS_STATUS_DONE.equals(response.status()) || response.approvedAt() == null) {
            log.warn("toss confirm response not done: paymentAttemptId={}, status={}",
                    paymentAttemptId, response.status());
            paymentConfirmTxService.recordReconciling(paymentAttemptId, memberId);
            return;
        }

        // 3. 승인 완료 저장 (실패하면 롤백되어 PROCESSING 유지 -> 별도 트랜잭션으로 결과 확인 중 기록)
        try {
            paymentConfirmTxService.completeApproval(paymentAttemptId, memberId, response.approvedAt().toInstant());
        } catch (RuntimeException e) {
            log.error("toss approved but db update failed: paymentAttemptId={}", paymentAttemptId, e);
            paymentConfirmTxService.recordReconciling(paymentAttemptId, memberId);
        }
    }

    // DB의 현재 상태로 응답 (승인 완료면 200, 그 외 처리 중, 결과 확인 중이면 202)
    private PaymentConfirmResult toResult(Long paymentAttemptId, Long memberId) {
        PaymentAttemptResponseDTO paymentAttempt = paymentService.getPaymentAttempt(paymentAttemptId, memberId);
        boolean approved = paymentAttempt.getStatus() == PaymentAttemptStatus.APPROVED;

        return new PaymentConfirmResult(paymentAttempt, approved);
    }

    // 대상(결제 시도)과 요청 본문으로 같은 요청인지 판단하는 해시
    private String requestHash(Long paymentAttemptId, PaymentConfirmRequestDTO request) {
        return IdempotencyHasher.hash(
                RESOURCE_PAYMENT_ATTEMPT + ":" + paymentAttemptId,
                request.getPaymentKey() + "|" + request.getOrderId() + "|" + request.getAmount());
    }
}