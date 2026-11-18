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

import java.util.Set;

// 결제 결과 확인 작업: Toss 결제 조회로 처리 중·결과 확인 중 결제를 승인 또는 실패로 확정
// @Transactional 없음: Toss를 기다리는 동안 DB 잠금과 커넥션을 잡고 있지 않으려고
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileHandler implements BackgroundJobHandler {

    private static final String TOSS_STATUS_DONE = "DONE";
    private static final Set<String> TOSS_FAILED_STATUSES = Set.of("ABORTED", "EXPIRED");
    private static final String NOT_FOUND_PAYMENT = "NOT_FOUND_PAYMENT";
    private static final String TOSS_UNAVAILABLE = "TOSS_UNAVAILABLE";
    private static final String RESULT_MISMATCH = "TOSS_RESULT_MISMATCH";
    private static final String APPLY_FAILED = "RECONCILE_APPLY_FAILED";

    private final PaymentReconcileTxService paymentReconcileTxService;
    private final BackgroundJobFinishService backgroundJobFinishService;
    private final TossPaymentClient tossPaymentClient;

    @Override
    public BackgroundJobType type() {
        return BackgroundJobType.PAYMENT_RECONCILE;
    }

    @Override
    public void handle(LeasedJob job) {
        PaymentReconcileTarget target = paymentReconcileTxService.findTarget(job.resourceId());

        // 1. 이미 확정된 결제(승인 API가 정상 처리 등)는 Toss를 부르지 않고 작업만 완료
        if (!target.isInProgress()) {
            backgroundJobFinishService.succeed(job);
            return;
        }

        // 2. Toss 결제 조회 (트랜잭션 밖, 잠금 없음)
        TossPaymentResponseDTO payment;
        try {
            payment = tossPaymentClient.getPayment(target.paymentKey());
        } catch (TossApiException e) {
            // Toss에 결제가 없음 -> 돈이 나가지 않은 것이 확실하므로 실패 확정
            if (NOT_FOUND_PAYMENT.equals(e.getTossCode())) {
                applyFailure(job, target, NOT_FOUND_PAYMENT);
                return;
            }
            log.warn("toss payment query rejected: paymentAttemptId={}, code={}",
                    target.paymentAttemptId(), e.getTossCode());
            backgroundJobFinishService.retryLater(job, e.getTossCode());
            return;
        } catch (TossUnavailableException | TossResultUnknownException e) {
            log.warn("toss payment query unavailable: paymentAttemptId={}, reason={}",
                    target.paymentAttemptId(), e.getMessage());
            backgroundJobFinishService.retryLater(job, TOSS_UNAVAILABLE);
            return;
        }

        // 3. Toss 결제 상태별 확정
        if (TOSS_STATUS_DONE.equals(payment.status())) {
            applyApproval(job, target, payment);
            return;
        }
        if (TOSS_FAILED_STATUSES.contains(payment.status())) {
            applyFailure(job, target, "TOSS_" + payment.status());
            return;
        }

        // 그 외(IN_PROGRESS 등): 아직 결과가 정해지지 않음
        backgroundJobFinishService.retryLater(job, "TOSS_STATUS_" + payment.status());
    }

    private void applyApproval(LeasedJob job, PaymentReconcileTarget target, TossPaymentResponseDTO payment) {
        // Toss 결과가 우리 결제와 같은지 확인 (다르면 확정하지 않고 관리자 확인까지 재시도)
        if (!target.orderId().equals(payment.orderId())
                || !target.amount().equals(payment.totalAmount())
                || payment.approvedAt() == null) {
            log.error("toss payment mismatch: paymentAttemptId={}, orderId={}, amount={}, tossOrderId={}, tossAmount={}",
                    target.paymentAttemptId(), target.orderId(), target.amount(),
                    payment.orderId(), payment.totalAmount());
            backgroundJobFinishService.retryLater(job, RESULT_MISMATCH);
            return;
        }

        try {
            paymentReconcileTxService.approve(job, target, payment.approvedAt().toInstant());
        } catch (RuntimeException e) {
            // 예매 확정 실패 등 -> 전체 롤백(결제는 결과 확인 중 유지), 다음에 다시
            log.error("payment reconcile approval failed: paymentAttemptId={}", target.paymentAttemptId(), e);
            backgroundJobFinishService.retryLater(job, APPLY_FAILED);
        }
    }

    private void applyFailure(LeasedJob job, PaymentReconcileTarget target, String failureCode) {
        try {
            paymentReconcileTxService.fail(job, target, failureCode);
        } catch (RuntimeException e) {
            log.error("payment reconcile failure record failed: paymentAttemptId={}", target.paymentAttemptId(), e);
            backgroundJobFinishService.retryLater(job, APPLY_FAILED);
        }
    }
}