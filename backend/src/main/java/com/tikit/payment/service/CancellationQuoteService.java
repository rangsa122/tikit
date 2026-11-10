package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.response.CancellationQuoteResponseDTO;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

// 전체 취소 예상 금액 조회 (조회만, 환불은 실행하지 않음)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CancellationQuoteService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 이미 진행 중인 취소 (이 상태가 있으면 새 취소 불가, 실패/완료는 제외)
    private static final List<CancellationStatus> IN_PROGRESS =
            List.of(CancellationStatus.REQUESTED, CancellationStatus.PROCESSING, CancellationStatus.RECONCILING);

    private final BookingRepository bookingRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationRepository cancellationRepository;
    private final Clock clock;

    public CancellationQuoteResponseDTO getQuote(Long memberId, Long bookingId) {
        String detail = "bookingId=" + bookingId + ", memberId=" + memberId;
        Instant now = Instant.now(clock);

        // 1. 본인 예매 (다른 회원 예매는 없는 예매와 같은 404)
        Booking booking = bookingRepository.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        // 2. 예매 생성 때 저장된 정책 버전으로 정책 결정 (회차 현재 정책 아님)
        CancellationPolicy policy = CancellationPolicy.from(booking.getPolicyVersionSnapshot());

        // 3. 승인된 결제 (확정 전 예매는 없으므로 결제액 0)
        PaymentAttempt approved = paymentAttemptRepository
                .findFirstByBookingIdAndStatus(bookingId, PaymentAttemptStatus.APPROVED)
                .orElse(null);
        Long paidAmount = approved == null ? 0L : approved.getAmount();

        // 4. 취소 불가 사유 확인 → 가능하면 정책으로 수수료·환불액 계산, 불가면 환불액 0
        CancellationIneligibleReason reason = findIneligibleReason(booking, approved, policy, now, detail);
        Long feeAmount = reason == null ? policy.calculateFee(paidAmount) : 0L;
        Long refundAmount = reason == null ? paidAmount - feeAmount : 0L;

        return CancellationQuoteResponseDTO.builder()
                .bookingId(booking.getId())
                .eligible(reason == null)
                .reasonCode(reason)
                .paidAmount(paidAmount)
                .feeAmount(feeAmount)
                .refundAmount(refundAmount)
                .policyVersion(policy.getVersion())
                .cancellationDeadline(toSeoul(booking.getCancellationDeadlineSnapshot()))
                .quotedAt(toSeoul(now))
                .serverTime(toSeoul(now))
                .build();
    }

    // 검사 순서: 이미 취소 -> 확정 여부 -> 진행 중 취소 -> 취소 기한 (해당 없으면 null = 취소 가능)
    private CancellationIneligibleReason findIneligibleReason(Booking booking, PaymentAttempt approved,
                                                              CancellationPolicy policy, Instant now, String detail) {
        if (booking.getStatus() == BookingStatus.CANCELED) {
            return CancellationIneligibleReason.ALREADY_CANCELED;
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return CancellationIneligibleReason.BOOKING_NOT_CONFIRMED;
        }
        // 확정 예매인데 승인된 결제가 없으면 데이터 불일치
        if (approved == null) {
            throw new IllegalStateException("확정 예매에 승인된 결제가 없습니다. " + detail);
        }
        if (cancellationRepository.existsByPaymentAttemptIdAndStatusIn(approved.getId(), IN_PROGRESS)) {
            return CancellationIneligibleReason.CANCELLATION_IN_PROGRESS;
        }
        if (!policy.isCancellable(now, booking.getCancellationDeadlineSnapshot())) {
            return CancellationIneligibleReason.CANCELLATION_DEADLINE_PASSED;
        }
        return null;
    }

    private OffsetDateTime toSeoul(Instant instant) {
        return instant.atZone(SEOUL).toOffsetDateTime();
    }
}