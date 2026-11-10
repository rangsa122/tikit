package com.tikit.payment.service;

import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.request.CancellationCreateRequestDTO;
import com.tikit.payment.dto.response.CancellationResponseDTO;
import com.tikit.payment.dto.toss.TossCancelRequestDTO;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

// 예매 전체 취소 지휘: 1차 트랜잭션 -> Toss 환불(트랜잭션 밖) -> 결과별 2차 트랜잭션 -> 현재 결과 조회
// @Transactional 없음: Toss를 기다리는 동안 DB 잠금과 커넥션을 잡고 있지 않으려고
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationService {

    private static final String ALREADY_CANCELED_PAYMENT = "ALREADY_CANCELED_PAYMENT";

    private final CancellationTxService cancellationTxService;
    private final CancellationQueryService cancellationQueryService;
    private final TossPaymentClient tossPaymentClient;

    public CancellationResponseDTO cancel(Long bookingId, Long memberId, CancellationCreateRequestDTO request) {
        // 1. 1차 트랜잭션 (검사 실패면 PaymentException이 그대로 Advice로 감, Toss 호출 없음)
        CancellationStart start = cancellationTxService.startCancellation(bookingId, memberId, request);

        // 2. Toss 환불 요청 후 결과별 기록
        refundWithToss(start, memberId);

        // 3. 어떤 경우든 마지막에 DB의 현재 상태로 응답
        return cancellationQueryService.getCancellation(start.cancellationId(), bookingId, memberId);
    }

    // Toss 취소 요청 후 결과별로 2차 트랜잭션 기록
    private void refundWithToss(CancellationStart start, Long memberId) {
        Long cancellationId = start.cancellationId();
        TossPaymentResponseDTO response;

        // 1. Toss 취소 요청 (환불액을 명시해 서버 계산보다 더 환불되지 않게, 멱등키는 취소 건에 저장된 키)
        try {
            response = tossPaymentClient.cancelPayment(
                    start.paymentKey(),
                    new TossCancelRequestDTO(start.reason(), start.refundAmount()),
                    start.pgIdempotencyKey()
            );
        } catch (TossApiException e) {
            // 이미 취소된 결제라는 거절은 실제로 환불됐을 수 있으므로 실패로 단정하지 않음 (복구 작업이 조회로 확인)
            if (ALREADY_CANCELED_PAYMENT.equals(e.getTossCode())) {
                log.warn("toss already canceled payment: cancellationId={}", cancellationId);
                cancellationTxService.recordReconciling(cancellationId);
                return;
            }
            // Toss가 거절 -> 실패 기록 (예매·좌석은 그대로, 응답 상태 FAILED로 안내)
            cancellationTxService.recordFailure(cancellationId, e.getTossCode());
            return;
        } catch (TossResultUnknownException | TossUnavailableException e) {
            // 응답 못 받음, Toss 장애 -> 결과 확인 중 (복구 작업이 확정될 때까지 조회)
            log.warn("toss refund result uncertain: cancellationId={}, reason={}", cancellationId, e.getMessage());
            cancellationTxService.recordReconciling(cancellationId);
            return;
        }

        // 2. 응답에서 우리 환불 거래를 못 찾으면 직접 확정하지 않고 결과 확인 중으로
        Optional<TossPaymentResponseDTO.Cancel> refund = findRefundTransaction(response, start.refundAmount());
        if (refund.isEmpty()) {
            log.warn("toss cancel response without refund transaction: cancellationId={}, status={}",
                    cancellationId, response.status());
            cancellationTxService.recordReconciling(cancellationId);
            return;
        }

        // 3. 환불 성공 저장 + 예매 취소 (실패하면 롤백되어 PROCESSING 유지 -> 별도 트랜잭션으로 결과 확인 중 기록)
        try {
            cancellationTxService.completeRefund(cancellationId, start.bookingId(), memberId,
                    refund.get().transactionKey(), refund.get().canceledAt().toInstant());
        } catch (RuntimeException e) {
            log.error("toss refunded but db update failed: cancellationId={}", cancellationId, e);
            cancellationTxService.recordReconciling(cancellationId);
        }
    }

    // cancels[] 중 우리 환불액과 같은 금액의 가장 최근 거래 (거래 키·취소 시각이 있어야 확정 가능)
    private Optional<TossPaymentResponseDTO.Cancel> findRefundTransaction(TossPaymentResponseDTO response, Long refundAmount) {
        if (response.cancels() == null) {
            return Optional.empty();
        }
        return response.cancels().stream()
                .filter(cancel -> refundAmount.equals(cancel.cancelAmount()))
                .filter(cancel -> cancel.transactionKey() != null && cancel.canceledAt() != null)
                .reduce((first, second) -> second);
    }
}