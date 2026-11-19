package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.response.CancellationResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// 예매 전체 취소 결과 조회 (DB의 현재 상태로 응답)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CancellationQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BookingRepository bookingRepository;
    private final CancellationRepository cancellationRepository;
    private final Clock clock;

    // 회원 본인의 취소 결과 조회 (없는 취소, 다른 회원의 취소는 모두 404)
    public CancellationResponseDTO getOwnedCancellation(Long cancellationId, Long memberId) {
        String detail = "cancellationId=" + cancellationId + ", memberId=" + memberId;

        Long bookingId = cancellationRepository.findOwnedBookingId(cancellationId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return getCancellation(cancellationId, bookingId, memberId);
    }

    // 회원 기준 응답 (재취소 가능 여부에 취소 기한 적용)
    public CancellationResponseDTO getCancellation(Long cancellationId, Long bookingId, Long memberId) {
        String detail = "cancellationId=" + cancellationId + ", bookingId=" + bookingId + ", memberId=" + memberId;
        Instant now = Instant.now(clock);

        Booking booking = bookingRepository.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        Cancellation cancellation = cancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return toResponse(cancellation, booking, isRetryAllowed(cancellation, booking, now, true), now);
    }

    // 관리자 기준 응답 (주인 상관없이 조회, 재취소 가능 여부에 취소 기한 미적용)
    public CancellationResponseDTO getAdminCancellation(Long cancellationId) {
        String detail = "cancellationId=" + cancellationId + ", admin";
        Instant now = Instant.now(clock);

        Long bookingId = cancellationRepository.findBookingIdById(cancellationId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        Cancellation cancellation = cancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return toResponse(cancellation, booking, isRetryAllowed(cancellation, booking, now, false), now);
    }

    private CancellationResponseDTO toResponse(Cancellation cancellation, Booking booking,
                                               boolean retryAllowed, Instant now) {
        return CancellationResponseDTO.builder()
                .cancellationId(cancellation.getId())
                .bookingId(booking.getId())
                .status(cancellation.getStatus())
                .refundAmount(cancellation.getRefundAmount())
                .feeAmount(cancellation.getFeeAmount())
                .reason(cancellation.getReason())
                .initiatorRole(cancellation.getInitiatorRole())
                .retryAllowed(retryAllowed)
                .failureCode(cancellation.getFailureCode())
                .createdAt(toSeoul(cancellation.getCreatedAt()))
                .completedAt(toSeoul(cancellation.getCompletedAt()))
                .serverTime(toSeoul(now))
                .build();
    }

    // 실패로 끝났고 예매가 아직 확정이면 다시 취소 가능 (회원은 예매 당시 정책의 기한 안이어야 함)
    private boolean isRetryAllowed(Cancellation cancellation, Booking booking, Instant now, boolean checkDeadline) {
        if (cancellation.getStatus() != CancellationStatus.FAILED || booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }
        if (!checkDeadline) {
            return true;
        }
        CancellationPolicy policy = CancellationPolicy.from(booking.getPolicyVersionSnapshot());
        return policy.isCancellable(now, booking.getCancellationDeadlineSnapshot());
    }

    private OffsetDateTime toSeoul(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}