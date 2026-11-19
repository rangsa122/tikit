package com.tikit.payment.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.common.ErrorCode;
import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobService;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// 관리자 결제·환불 결과 재확인 요청의 트랜잭션 단위 처리 (Toss는 부르지 않고 복구 작업만 바로 실행되게 함)
// 대상 행은 잠그지 않음: 확인 직후 대상이 확정돼도 복구 작업이 "이미 확정"을 보고 바로 끝나므로 안전
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReconciliationTxService {

    // 결과가 아직 확정되지 않은 결제 (READY는 결제 전, APPROVED·FAILED는 확정)
    private static final List<PaymentAttemptStatus> PAYMENT_UNRESOLVED =
            List.of(PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.RECONCILING);

    // 결과가 아직 확정되지 않은 취소 (REQUESTED는 저장 때 바로 PROCESSING이 되어 DB에 남지 않음)
    private static final List<CancellationStatus> CANCELLATION_UNRESOLVED =
            List.of(CancellationStatus.PROCESSING, CancellationStatus.RECONCILING);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationRepository cancellationRepository;
    private final BackgroundJobService backgroundJobService;
    private final Clock clock;

    // 결제 결과 재확인: 관리자 확인 -> 미확정 결제인지 확인 -> 결제 복구 작업을 바로 실행되게
    @Transactional
    public ReconciliationAccepted requestPaymentRecheck(Long paymentAttemptId, Long adminId) {
        String detail = "paymentAttemptId=" + paymentAttemptId + ", adminId=" + adminId;
        requireAdmin(adminId, detail);

        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentAttemptId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        if (!PAYMENT_UNRESOLVED.contains(attempt.getStatus())) {
            throw new PaymentException(ErrorCode.STATE_CONFLICT, detail + ", status=" + attempt.getStatus());
        }

        return accept(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId, detail);
    }

    // 환불 결과 재확인: 관리자 확인 -> 미확정 취소인지 확인 -> 환불 복구 작업을 바로 실행되게
    // 재확인만 함 (미실행이 확정되면 복구 작업이 FAILED로 끝내고, 다시 환불은 관리자 전체 환불로 새로 요청)
    @Transactional
    public ReconciliationAccepted requestCancellationRecheck(Long cancellationId, Long adminId) {
        String detail = "cancellationId=" + cancellationId + ", adminId=" + adminId;
        requireAdmin(adminId, detail);

        Cancellation cancellation = cancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        if (!CANCELLATION_UNRESOLVED.contains(cancellation.getStatus())) {
            throw new PaymentException(ErrorCode.STATE_CONFLICT, detail + ", status=" + cancellation.getStatus());
        }

        return accept(BackgroundJobType.CANCELLATION_RECONCILE, cancellationId, detail);
    }

    // 공통: 작업 예약 후 응답에 쓸 값만 꺼냄 (사유는 지휘 서비스가 로그로 남김)
    private ReconciliationAccepted accept(BackgroundJobType type, Long resourceId, String detail) {
        Instant now = Instant.now(clock);
        BackgroundJob job = backgroundJobService.requestRecheck(type, resourceId, now);

        log.info("admin reconciliation requested: {}, jobId={}, jobStatus={}", detail, job.getId(), job.getStatus());
        return new ReconciliationAccepted(job.getId(), job.getStatus(), resourceId, now);
    }

    // 관리자 확인 (토큰이 아니라 현재 DB 기준: 정지됐거나 역할이 바뀐 계정 차단) - startAdminCancellation과 같은 규칙
    private void requireAdmin(Long adminId, String detail) {
        Member admin = paymentAttemptRepository.findMemberForUpdate(adminId)
                .orElseThrow(() -> new PaymentException(ErrorCode.ACCESS_DENIED, detail));
        if (admin.getStatus() != MemberStatus.ACTIVE) {
            throw new PaymentException(ErrorCode.ACCOUNT_RESTRICTED, detail);
        }
        if (admin.getRole() != Role.ADMIN) {
            throw new PaymentException(ErrorCode.ACCESS_DENIED, detail);
        }
    }
}