package com.tikit.payment.service;

import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobFinishService;
import com.tikit.common.job.LeasedJob;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

// 결제 결과 확인 작업의 DB 처리 (Toss 조회는 PaymentReconcileHandler가 트랜잭션 밖에서)
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconcileTxService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentConfirmTxService paymentConfirmTxService;
    private final BackgroundJobFinishService backgroundJobFinishService;

    // 확인할 결제 정보 읽기 (잠금 없음, 결과 반영 때 다시 잠그고 확인)
    @Transactional(readOnly = true)
    public PaymentReconcileTarget findTarget(Long paymentAttemptId) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentAttemptId)
                .orElseThrow(() -> new IllegalStateException("payment attempt missing: paymentAttemptId=" + paymentAttemptId));
        Long memberId = paymentAttemptRepository.findMemberIdById(paymentAttemptId)
                .orElseThrow(() -> new IllegalStateException("booking missing: paymentAttemptId=" + paymentAttemptId));

        return new PaymentReconcileTarget(
                attempt.getId(),
                memberId,
                attempt.getStatus(),
                attempt.getPaymentKey(),
                attempt.getOrderId(),
                attempt.getAmount()
        );
    }

    // Toss 승인 완료 확인: 점유 확인 → 결제 승인·예매 확정 → 작업 완료 (한 트랜잭션)
    @Transactional
    public void approve(LeasedJob job, PaymentReconcileTarget target, Instant approvedAt) {
        Optional<BackgroundJob> lockedJob = backgroundJobFinishService.lockLeased(job);
        if (lockedJob.isEmpty()) {
            return;
        }

        paymentConfirmTxService.completeApproval(target.paymentAttemptId(), target.memberId(), approvedAt);
        lockedJob.get().succeed(job.owner());

        log.info("payment reconciled as approved: paymentAttemptId={}", target.paymentAttemptId());
    }

    // Toss 미승인 확인: 점유 확인 → 결제 실패 기록 → 작업 완료 (한 트랜잭션, 예매·좌석은 유지)
    @Transactional
    public void fail(LeasedJob job, PaymentReconcileTarget target, String failureCode) {
        Optional<BackgroundJob> lockedJob = backgroundJobFinishService.lockLeased(job);
        if (lockedJob.isEmpty()) {
            return;
        }

        paymentConfirmTxService.recordFailure(target.paymentAttemptId(), target.memberId(), failureCode);
        lockedJob.get().succeed(job.owner());

        log.info("payment reconciled as failed: paymentAttemptId={}, failureCode={}",
                target.paymentAttemptId(), failureCode);
    }
}