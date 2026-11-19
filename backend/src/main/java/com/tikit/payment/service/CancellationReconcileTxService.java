package com.tikit.payment.service;

import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobFinishService;
import com.tikit.common.job.LeasedJob;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

// 환불 결과 확인 작업의 DB 처리 (Toss 조회는 CancellationReconcileHandler가 트랜잭션 밖에서)
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationReconcileTxService {

    private final CancellationRepository cancellationRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationTxService cancellationTxService;
    private final BackgroundJobFinishService backgroundJobFinishService;

    // 확인할 취소 정보 읽기 (잠금 없음, 결과 반영 때 다시 잠그고 확인)
    // 취소 -> 결제 시도(paymentKey, 예매 ID) -> 예매 주인 회원 ID
    @Transactional(readOnly = true)
    public CancellationReconcileTarget findTarget(Long cancellationId) {
        Cancellation cancellation = cancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new IllegalStateException("cancellation missing: cancellationId=" + cancellationId));
        PaymentAttempt attempt = paymentAttemptRepository.findById(cancellation.getPaymentAttemptId())
                .orElseThrow(() -> new IllegalStateException("payment attempt missing: cancellationId=" + cancellationId));
        Long memberId = paymentAttemptRepository.findMemberIdById(attempt.getId())
                .orElseThrow(() -> new IllegalStateException("booking missing: cancellationId=" + cancellationId));

        return new CancellationReconcileTarget(
                cancellation.getId(),
                attempt.getBookingId(),
                memberId,
                cancellation.getStatus(),
                attempt.getPaymentKey(),
                cancellation.getRefundAmount()
        );
    }

    // Toss 환불 거래 확인: 점유 확인 -> 취소 성공·예매 취소(좌석 해제, 티켓 무효) -> 작업 완료 (한 트랜잭션)
    @Transactional
    public void refund(LeasedJob job, CancellationReconcileTarget target, String pgTransactionKey, Instant completedAt) {
        Optional<BackgroundJob> lockedJob = backgroundJobFinishService.lockLeased(job);
        if (lockedJob.isEmpty()) {
            return;
        }

        cancellationTxService.completeRefund(target.cancellationId(), target.bookingId(), target.memberId(),
                pgTransactionKey, completedAt);
        lockedJob.get().succeed(job.owner());

        log.info("cancellation reconciled as refunded: cancellationId={}, pgTransactionKey={}",
                target.cancellationId(), pgTransactionKey);
    }

    // 환불되지 않음 확인: 점유 확인 -> 취소 실패 기록 -> 작업 완료 (한 트랜잭션, 예매·좌석·티켓은 유지)
    @Transactional
    public void fail(LeasedJob job, CancellationReconcileTarget target, String failureCode) {
        Optional<BackgroundJob> lockedJob = backgroundJobFinishService.lockLeased(job);
        if (lockedJob.isEmpty()) {
            return;
        }

        cancellationTxService.recordFailure(target.cancellationId(), failureCode);
        lockedJob.get().succeed(job.owner());

        log.info("cancellation reconciled as failed: cancellationId={}, failureCode={}",
                target.cancellationId(), failureCode);
    }
}