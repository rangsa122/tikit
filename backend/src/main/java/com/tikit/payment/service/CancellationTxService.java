package com.tikit.payment.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.service.BookingCancelService;
import com.tikit.common.ErrorCode;
import com.tikit.common.job.BackgroundJobService;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.payment.dto.request.CancellationCreateRequestDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationInitiatorRole;
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
import java.util.UUID;

// 예매 전체 취소의 트랜잭션 단위 처리 (Toss 호출은 이 클래스 밖에서)
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationTxService {

    // 진행 중 취소 (견적과 같은 기준: 환불됐을 수 있는 RECONCILING 포함, FAILED는 재시도 가능하게 제외)
    private static final List<CancellationStatus> IN_PROGRESS =
            List.of(CancellationStatus.REQUESTED, CancellationStatus.PROCESSING, CancellationStatus.RECONCILING);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationRepository cancellationRepository;
    private final BookingCancelService bookingCancelService;
    private final BackgroundJobService backgroundJobService;
    private final Clock clock;

    // 1차(회원): 본인 예매 잠금 -> 공통 검사 -> 취소 요청 저장(처리 중) -> 커밋
    @Transactional
    public CancellationStart startCancellation(Long bookingId, Long memberId, CancellationCreateRequestDTO request) {
        String detail = "bookingId=" + bookingId + ", memberId=" + memberId;

        // 1. 예매 잠금 (같은 예매 취소가 동시에 와도 하나씩 처리, 다른 회원 예매는 404)
        Booking booking = paymentAttemptRepository.findOwnedBookingForUpdate(bookingId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return start(bookingId, booking, memberId, CancellationInitiatorRole.MEMBER, request, true, detail);
    }

    // 1차(관리자): 관리자 확인 -> 예매 잠금(주인 상관없음) -> 공통 검사(기한 제외) -> 취소 요청 저장 -> 커밋
    // 중복 환불·처리 중 충돌·견적 재검증은 회원 취소와 같은 코드를 타므로 우회할 수 없음
    @Transactional
    public CancellationStart startAdminCancellation(Long bookingId, Long adminId, CancellationCreateRequestDTO request) {
        String detail = "bookingId=" + bookingId + ", adminId=" + adminId;

        // 1. 관리자 확인 (토큰이 아니라 현재 DB 기준: 정지됐거나 역할이 바뀐 계정 차단, 회원 -> 예매 -> 결제 잠금 순서)
        Member admin = paymentAttemptRepository.findMemberForUpdate(adminId)
                .orElseThrow(() -> new PaymentException(ErrorCode.ACCESS_DENIED, detail));
        if (admin.getStatus() != MemberStatus.ACTIVE) {
            throw new PaymentException(ErrorCode.ACCOUNT_RESTRICTED, detail);
        }
        if (admin.getRole() != Role.ADMIN) {
            throw new PaymentException(ErrorCode.ACCESS_DENIED, detail);
        }

        // 2. 예매 잠금 (관리자는 모든 회원의 예매를 환불할 수 있음)
        Booking booking = paymentAttemptRepository.findBookingByIdForUpdate(bookingId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return start(bookingId, booking, adminId, CancellationInitiatorRole.ADMIN, request, false, detail);
    }

    // 1차 공통: 확정 확인 -> 결제 잠금 -> 진행 중 취소 차단 -> (회원만) 기한 확인 -> 견적 재검증 -> 저장 -> 결과 확인 작업 예약
    // checkDeadline=false(관리자): 운영 사유로 기한 후에도 취소 가능. 금액은 같은 정책으로 서버가 계산
    // (v1은 수수료가 항상 0. 시각별 수수료가 생기면 관리자 환불 금액 정책을 따로 정해야 함)
    private CancellationStart start(Long bookingId, Booking booking, Long requestedBy,
                                    CancellationInitiatorRole initiatorRole, CancellationCreateRequestDTO request,
                                    boolean checkDeadline, String detail) {
        Instant now = Instant.now(clock);

        // 2. 확정 예매만 취소 가능 (결제 전 예매는 선점 해제 API, 이미 취소면 불가)
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new PaymentException(ErrorCode.CANCELLATION_NOT_ALLOWED, detail + ", status=" + booking.getStatus());
        }

        // 3. 승인된 결제 잠금 (확정 예매인데 없으면 데이터 불일치)
        PaymentAttempt approved = lockApprovedAttempt(bookingId, detail);

        // 4. 진행 중 취소가 있으면 거절 (이중 환불 방지, 관리자도 우회 불가)
        if (cancellationRepository.existsByPaymentAttemptIdAndStatusIn(approved.getId(), IN_PROGRESS)) {
            throw new PaymentException(ErrorCode.CANCELLATION_IN_PROGRESS, detail);
        }

        // 5. 예매 당시 정책으로 기한 확인 (관리자는 건너뜀)
        CancellationPolicy policy = CancellationPolicy.from(booking.getPolicyVersionSnapshot());
        if (checkDeadline && !policy.isCancellable(now, booking.getCancellationDeadlineSnapshot())) {
            throw new PaymentException(ErrorCode.CANCELLATION_NOT_ALLOWED, detail + ", deadline passed");
        }

        // 6. 서버 재계산 금액·정책이 화면에서 본 견적과 다르면 실행하지 않음
        Long paidAmount = approved.getAmount();
        Long feeAmount = policy.calculateFee(paidAmount);
        Long refundAmount = paidAmount - feeAmount;
        if (!refundAmount.equals(request.getExpectedRefundAmount())
                || !policy.getVersion().equals(request.getPolicyVersion())) {
            throw new PaymentException(ErrorCode.CANCELLATION_QUOTE_CHANGED, detail
                    + ", expected=" + request.getExpectedRefundAmount() + "/" + request.getPolicyVersion()
                    + ", actual=" + refundAmount + "/" + policy.getVersion());
        }

        // 7. 취소 요청 저장 -> 처리 중 (Toss 멱등키는 이 취소 건에 고정, 재시도·복구 때 같은 키 재사용)
        Cancellation cancellation = Cancellation.create(approved.getId(), requestedBy, initiatorRole,
                request.getReason(), paidAmount, feeAmount, policy.getVersion(), UUID.randomUUID().toString());
        cancellation.startProcessing();
        cancellationRepository.save(cancellation);

        // 8. 결과 확인 작업 예약 (같은 트랜잭션: Toss 취소 중 서버가 멈춰도 처리 중으로 남지 않게 함)
        backgroundJobService.schedule(BackgroundJobType.CANCELLATION_RECONCILE, cancellation.getId(), now);

        log.info("cancellation started: {}, cancellationId={}, initiatorRole={}, refundAmount={}",
                detail, cancellation.getId(), initiatorRole, refundAmount);

        // 환불 반영(completeRefund)은 예매 주인 기준 -> 예매 주인 ID를 함께 넘김
        return new CancellationStart(cancellation.getId(), bookingId, booking.getMemberId(), approved.getPaymentKey(),
                request.getReason(), refundAmount, cancellation.getPgIdempotencyKey());
    }

    // 2차(환불 성공): 예매 잠금 -> 취소 잠금 -> 환불 성공 기록 + 예매 전체 취소(좌석 해제, 티켓 무효)
    // 예매 취소가 실패하면 환불 성공 기록까지 롤백 -> 호출한 쪽이 결과 확인 중으로 기록 (복구 작업이 다시 확정)
    @Transactional
    public void completeRefund(Long cancellationId, Long bookingId, Long memberId,
                               String pgTransactionKey, Instant completedAt) {
        String detail = "cancellationId=" + cancellationId + ", bookingId=" + bookingId + ", memberId=" + memberId;

        // 1. 예매 잠금 -> 취소 잠금 (1차와 같은 순서, A파트 cancelConfirmed는 예매 행이 잠겨 있어야 함)
        Booking booking = paymentAttemptRepository.findOwnedBookingForUpdate(bookingId, memberId)
                .orElseThrow(() -> new IllegalStateException("취소할 예매를 찾을 수 없습니다. " + detail));
        Cancellation cancellation = lockCancellation(cancellationId, detail);

        // 2. 복구 작업이 먼저 성공 처리했으면 이미 끝난 일 (예매 취소도 함께 끝난 상태)
        if (cancellation.getStatus() == CancellationStatus.SUCCEEDED) {
            log.info("refund already succeeded: {}", detail);
            return;
        }

        // 3. 환불 성공 기록 + 예매 전체 취소 (같은 트랜잭션, 하나라도 실패하면 함께 롤백)
        cancellation.succeed(pgTransactionKey, completedAt);
        bookingCancelService.cancelConfirmed(booking, completedAt);

        log.info("refund succeeded: {}, pgTransactionKey={}", detail, pgTransactionKey);
    }

    // 2차(Toss 거절): 실패 기록 (예매·좌석·티켓은 그대로, 같은 예매로 다시 취소 가능)
    @Transactional
    public void recordFailure(Long cancellationId, String failureCode) {
        String detail = "cancellationId=" + cancellationId;
        Cancellation cancellation = lockCancellation(cancellationId, detail);

        // 이미 성공·실패로 끝났으면 바꾸지 않음 (성공을 실패로 덮으면 환불됐는데 예매가 살아 있게 됨)
        if (cancellation.getStatus() != CancellationStatus.PROCESSING
                && cancellation.getStatus() != CancellationStatus.RECONCILING) {
            log.error("finished cancellation cannot be failed: {}, status={}, failureCode={}",
                    detail, cancellation.getStatus(), failureCode);
            return;
        }

        cancellation.fail(failureCode);
        log.info("refund failed: {}, failureCode={}", detail, failureCode);
    }

    // 2차(결과 모름): 응답 유실·Toss 장애 -> 결과 확인 중 (복구 작업이 Toss 조회로 확정할 때까지 재시도)
    @Transactional
    public void recordReconciling(Long cancellationId) {
        String detail = "cancellationId=" + cancellationId;
        Cancellation cancellation = lockCancellation(cancellationId, detail);

        if (cancellation.getStatus() != CancellationStatus.PROCESSING) {
            log.info("cancellation not in processing, skip reconciling: {}, status={}", detail, cancellation.getStatus());
            return;
        }

        cancellation.markReconciling();
        log.warn("refund result unknown, reconciling: {}", detail);
    }

    private PaymentAttempt lockApprovedAttempt(Long bookingId, String detail) {
        PaymentAttempt approved = paymentAttemptRepository
                .findFirstByBookingIdAndStatus(bookingId, PaymentAttemptStatus.APPROVED)
                .orElseThrow(() -> new IllegalStateException("확정 예매에 승인된 결제가 없습니다. " + detail));
        return paymentAttemptRepository.findByIdForUpdate(approved.getId())
                .orElseThrow(() -> new IllegalStateException("승인된 결제를 잠글 수 없습니다. " + detail));
    }

    private Cancellation lockCancellation(Long cancellationId, String detail) {
        return cancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(() -> new IllegalStateException("취소 요청을 찾을 수 없습니다. " + detail));
    }
}