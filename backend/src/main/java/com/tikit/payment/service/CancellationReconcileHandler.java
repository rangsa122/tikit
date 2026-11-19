package com.tikit.payment.service;

import com.tikit.common.job.BackgroundJobFinishService;
import com.tikit.common.job.BackgroundJobHandler;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.common.job.LeasedJob;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 환불 결과 확인 작업: Toss 결제 조회의 cancels[]로 처리 중·결과 확인 중 취소를 성공 또는 실패로 확정
// @Transactional 없음: Toss를 기다리는 동안 DB 잠금과 커넥션을 잡고 있지 않으려고
@Slf4j
@Component
@RequiredArgsConstructor
public class CancellationReconcileHandler implements BackgroundJobHandler {

    private static final String TOSS_STATUS_DONE = "DONE";
    private static final String NOT_CANCELED = "TOSS_NOT_CANCELED";
    private static final String TOSS_UNAVAILABLE = "TOSS_UNAVAILABLE";
    private static final String APPLY_FAILED = "RECONCILE_APPLY_FAILED";

    private final CancellationReconcileTxService cancellationReconcileTxService;
    private final BackgroundJobFinishService backgroundJobFinishService;
    private final TossPaymentClient tossPaymentClient;

    @Override
    public BackgroundJobType type() {
        return BackgroundJobType.CANCELLATION_RECONCILE;
    }

    @Override
    public void handle(LeasedJob job) {
        CancellationReconcileTarget target = cancellationReconcileTxService.findTarget(job.resourceId());

        // 1. 이미 확정된 취소(취소 API가 정상 처리 등)는 Toss를 부르지 않고 작업만 완료
        if (!target.isInProgress()) {
            backgroundJobFinishService.succeed(job);
            return;
        }

        // 2. Toss 결제 조회 (트랜잭션 밖, 잠금 없음) - 취소 거래는 결제 응답의 cancels[]에 있음
        TossPaymentResponseDTO payment;
        try {
            payment = tossPaymentClient.getPayment(target.paymentKey());
        } catch (TossApiException e) {
            // 승인된 결제를 Toss가 거절·못 찾음 -> 확정하지 않고 관리자 확인까지 재시도
            log.warn("toss payment query rejected: cancellationId={}, code={}",
                    target.cancellationId(), e.getTossCode());
            backgroundJobFinishService.retryLater(job, e.getTossCode());
            return;
        } catch (TossUnavailableException | TossResultUnknownException e) {
            log.warn("toss payment query unavailable: cancellationId={}, reason={}",
                    target.cancellationId(), e.getMessage());
            backgroundJobFinishService.retryLater(job, TOSS_UNAVAILABLE);
            return;
        }

        // 3. 우리 환불 거래가 있으면 성공 확정 (실패 판단보다 먼저)
        Optional<TossPaymentResponseDTO.Cancel> refund = findRefundTransaction(payment, target.refundAmount());
        if (refund.isPresent()) {
            applyRefund(job, target, refund.get());
            return;
        }

        // 4. 환불 거래가 없고 결제가 그대로(DONE)면 환불되지 않은 것이 확실 -> 실패 확정
        if (TOSS_STATUS_DONE.equals(payment.status())) {
            applyFailure(job, target, NOT_CANCELED);
            return;
        }

        // 5. 그 외(취소됐는데 금액이 다름 등): 확정하지 않고 관리자 확인까지 재시도
        log.error("toss cancel result unclear: cancellationId={}, status={}, refundAmount={}",
                target.cancellationId(), payment.status(), target.refundAmount());
        backgroundJobFinishService.retryLater(job, "TOSS_STATUS_" + payment.status());
    }

    // cancels[] 중 우리 환불액과 같은 금액의 가장 최근 거래 (CancellationService와 같은 기준)
    private Optional<TossPaymentResponseDTO.Cancel> findRefundTransaction(TossPaymentResponseDTO payment, Long refundAmount) {
        if (payment.cancels() == null) {
            return Optional.empty();
        }
        return payment.cancels().stream()
                .filter(cancel -> refundAmount.equals(cancel.cancelAmount()))
                .filter(cancel -> cancel.transactionKey() != null && cancel.canceledAt() != null)
                .reduce((first, second) -> second);
    }

    private void applyRefund(LeasedJob job, CancellationReconcileTarget target, TossPaymentResponseDTO.Cancel refund) {
        try {
            cancellationReconcileTxService.refund(job, target, refund.transactionKey(), refund.canceledAt().toInstant());
        } catch (RuntimeException e) {
            // 예매 취소 실패 등 -> 전체 롤백(취소는 결과 확인 중 유지), 다음에 다시
            log.error("cancellation reconcile refund failed: cancellationId={}", target.cancellationId(), e);
            backgroundJobFinishService.retryLater(job, APPLY_FAILED);
        }
    }

    private void applyFailure(LeasedJob job, CancellationReconcileTarget target, String failureCode) {
        try {
            cancellationReconcileTxService.fail(job, target, failureCode);
        } catch (RuntimeException e) {
            log.error("cancellation reconcile failure record failed: cancellationId={}", target.cancellationId(), e);
            backgroundJobFinishService.retryLater(job, APPLY_FAILED);
        }
    }
}