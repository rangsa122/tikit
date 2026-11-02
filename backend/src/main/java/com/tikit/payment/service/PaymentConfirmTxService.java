package com.tikit.payment.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.request.PaymentConfirmRequestDTO;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmTxService {

    private final PaymentAttemptRepository paymentAttemptRepository;

    // 승인 1차 트랜잭션: 회원 -> 예매 -> 결제 순으로 잠그고 검사한 뒤 PROCESSING 저장 (커밋 후 Toss 호출)
    @Transactional
    public ConfirmationStart startConfirmation(Long paymentAttemptId, Long memberId,
                                               PaymentConfirmRequestDTO request) {
        String detail = "paymentAttemptId=" + paymentAttemptId + ", memberId=" + memberId;
        Instant now = Instant.now();

        // 1. 결제 시도의 예매 ID 조회 (없으면 404)
        Long bookingId = paymentAttemptRepository.findBookingIdById(paymentAttemptId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        // 2. 회원 잠금
        Member member = paymentAttemptRepository.findMemberForUpdate(memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        // 3. 본인 예매 잠금 (다른 회원 결제면 결과 없음 -> 없는 결제와 같은 404)
        Booking booking = paymentAttemptRepository.findOwnedBookingForUpdate(bookingId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        // 토큰이 아닌 현재 DB 기준으로 회원 상태와 역할 확인
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new PaymentException(ErrorCode.ACCOUNT_RESTRICTED, detail);
        }
        if (member.getRole() != Role.MEMBER) {
            throw new PaymentException(ErrorCode.ACCESS_DENIED, detail);
        }

        // 4. 결제 시도 잠금 (잠금 후 읽어서 최신 상태 보장)
        PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(paymentAttemptId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        // 요청 주문 번호, 금액을 DB 값과 대조 (요청 값은 믿지 않음)
        if (!attempt.getOrderId().equals(request.getOrderId())
                || !attempt.getAmount().equals(request.getAmount())) {
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, detail);
        }

        // 이미 승인된 결제는 Toss를 다시 부르지 않고 현재 결과 반환 (200)
        if (attempt.isApproved()) {
            return toResult(ConfirmationStartStatus.ALREADY_APPROVED, attempt);
        }

        // 처리 중 또는 결과 확인 중이면 Toss를 다시 부르지 않음 (202, 프론트가 조회 API로 재조회)
        if (attempt.isInProgress()) {
            return toResult(ConfirmationStartStatus.IN_PROGRESS, attempt);
        }

        // READY가 아니면(FAILED 등) 이 결제 시도로는 승인 불가 (409)
        if (!attempt.isReady()) {
            throw new PaymentException(ErrorCode.INVALID_PAYMENT_STATE, detail);
        }

        // 예매가 결제 대기 중이 아니면 승인 불가 (만료면 BOOKING_EXPIRED, 확정, 취소면 INVALID_PAYMENT_STATE)
        if (!booking.isPendingPayment()) {
            ErrorCode errorCode = booking.getStatus() == BookingStatus.EXPIRED
                    ? ErrorCode.BOOKING_EXPIRED
                    : ErrorCode.INVALID_PAYMENT_STATE;
            throw new PaymentException(errorCode, detail);
        }

        // 결제 기한(제한시간) 확인 (예매 파트와 같은 기준: now >= expiresAt이면 만료)
        if (booking.isExpired(now)) {
            throw new PaymentException(ErrorCode.BOOKING_EXPIRED, detail);
        }

        // PROCESSING에 저장 (같은 결제의 다음 승인 요청은 이 상태를 보고 Toss를 다시 부르지 않음)
        attempt.startConfirmation(request.getPaymentKey(), now);

        // TODO 복구 작업 저장

        log.info("payment confirmation started: paymentAttemptId={}, bookingId={}", paymentAttemptId, bookingId);

        return toResult(ConfirmationStartStatus.STARTED, attempt);
    }

    // 승인 2차 트랜잭션 (Toss 승인 성공): 예매 -> 결제 순으로 잠그고 APPROVED 저장
    @Transactional
    public void completeApproval(Long paymentAttemptId, Long memberId, Instant approvedAt) {
        String detail = "paymentAttemptId=" + paymentAttemptId + ", memberId=" + memberId;

        // 1. 예매 잠금 -> 결제 시도 잠금 (1차와 같은 순서, 회원은 다시 볼 필요 없어 생략)
        Booking booking = lockBooking(paymentAttemptId, memberId, detail);
        PaymentAttempt attempt = lockAttempt(paymentAttemptId, detail);

        // 2. 복구 작업이 먼저 승인 처리했으면 이미 끝난 일이므로 넘어감
        if (attempt.isApproved()) {
            log.info("payment already approved: {}", detail);
            return;
        }

        // 3. 승인 완료 (FAILED에서 오면 엔티티가 예외 -> 롤백, 지휘 서비스가 결과 확인 중으로 처리)
        attempt.approve(approvedAt);

        // TODO Y-06(11/11): A파트 예매 확정 메서드 호출 (예매 CONFIRMED, 좌석 SOLD, 티켓 발급)

        log.info("payment approved: {}, bookingId={}", detail, booking.getId());
    }

    // 승인 2차 트랜잭션 (Toss 거절): FAILED 저장, 예매와 좌석은 유지 (기한 안이면 새 시도로 재결제)
    @Transactional
    public void recordFailure(Long paymentAttemptId, Long memberId, String failureCode) {
        String detail = "paymentAttemptId=" + paymentAttemptId + ", memberId=" + memberId;

        // 1. 예매 잠금 -> 결제 시도 잠금
        lockBooking(paymentAttemptId, memberId, detail);
        PaymentAttempt attempt = lockAttempt(paymentAttemptId, detail);

        // 2. 이미 승인된 결제는 실패로 바꾸지 않음 (돈이 나간 결제, 관리자 확인 필요)
        if (attempt.isApproved()) {
            log.error("approved payment cannot be failed: {}, failureCode={}", detail, failureCode);
            return;
        }

        // 3. 이미 실패로 기록됐으면 넘어감
        if (attempt.getStatus() == PaymentAttemptStatus.FAILED) {
            log.info("payment already failed: {}", detail);
            return;
        }

        // 4. 실패 기록
        attempt.fail(failureCode);

        log.info("payment failed: {}, failureCode={}", detail, failureCode);
    }

    // 승인 2차 트랜잭션 (Toss 응답 없음 또는 승인 후 DB 반영 실패): RECONCILING 저장, 복구 작업이 확정
    @Transactional
    public void recordReconciling(Long paymentAttemptId, Long memberId) {
        String detail = "paymentAttemptId=" + paymentAttemptId + ", memberId=" + memberId;

        // 1. 예매 잠금 -> 결제 시도 잠금
        lockBooking(paymentAttemptId, memberId, detail);
        PaymentAttempt attempt = lockAttempt(paymentAttemptId, detail);

        // 2. 그 사이 복구 작업이 확정했으면 되돌리지 않음 (PROCESSING일 때만 변경)
        if (attempt.getStatus() != PaymentAttemptStatus.PROCESSING) {
            log.info("payment not in processing, skip reconciling: {}, status={}", detail, attempt.getStatus());
            return;
        }

        // 3. 결과 확인 중 기록
        attempt.markReconciling();

        log.warn("payment result unknown, reconciling: {}", detail);
    }

    // 2차 트랜잭션용 예매 잠금 (1차를 통과한 결제라 없으면 데이터 이상 -> 404가 아닌 IllegalStateException)
    private Booking lockBooking(Long paymentAttemptId, Long memberId, String detail) {
        Long bookingId = paymentAttemptRepository.findBookingIdById(paymentAttemptId)
                .orElseThrow(() -> new IllegalStateException("payment attempt missing in second transaction: " + detail));

        return paymentAttemptRepository.findOwnedBookingForUpdate(bookingId, memberId)
                .orElseThrow(() -> new IllegalStateException("booking missing in second transaction: " + detail));
    }

    // 2차 트랜잭션용 결제 시도 잠금 (반드시 lockBooking 다음에 호출)
    private PaymentAttempt lockAttempt(Long paymentAttemptId, String detail) {
        return paymentAttemptRepository.findByIdForUpdate(paymentAttemptId)
                .orElseThrow(() -> new IllegalStateException("payment attempt missing in second transaction: " + detail));
    }

    // 엔티티에서 Toss 호출에 필요한 값만 꺼내 결과로 반환
    private ConfirmationStart toResult(ConfirmationStartStatus status, PaymentAttempt attempt) {
        return new ConfirmationStart(
                status,
                attempt.getId(),
                attempt.getPaymentKey(),
                attempt.getOrderId(),
                attempt.getAmount(),
                attempt.getPgIdempotencyKey()
        );
    }
}