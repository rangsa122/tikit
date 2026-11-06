package com.tikit.booking.dto.response;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.PaymentAttempt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record BookingResponseDTO(
        Long bookingId,
        Long sessionId,
        Long performanceId,
        String performanceTitle,
        OffsetDateTime startsAt,
        BookingStatus status,
        List<BookedSeat> seats,
        Long totalAmount,
        OffsetDateTime expiresAt,
        Long paymentAttemptId,
        String paymentStatus,
        Long cancellationId,
        String cancellationStatus,
        OffsetDateTime createdAt,
        OffsetDateTime serverTime
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public record BookedSeat(Long seatId, String label, String grade, Long price) {

        public static BookedSeat from(BookingSeat seat) {
            return new BookedSeat(seat.getSessionSeatId(), seat.getSeatLabelSnapshot(),
                    seat.getGradeSnapshot(), seat.getPriceSnapshot());
        }
    }

    // 예매 생성과 선점 해제 응답 (결제와 취소 정보 없음)
    public static BookingResponseDTO of(Booking booking, Long performanceId,
                                        List<BookingSeat> seats, Instant now) {
        return of(booking, performanceId, seats, null, null, now);
    }

    // 예매 조회 응답 (최신 결제 시도와 취소 포함, 없으면 null)
    public static BookingResponseDTO of(Booking booking, Long performanceId, List<BookingSeat> seats,
                                        PaymentAttempt paymentAttempt, Cancellation cancellation, Instant now) {
        return new BookingResponseDTO(
                booking.getId(),
                booking.getSessionId(),
                performanceId,
                booking.getPerformanceTitleSnapshot(),
                toSeoul(booking.getStartsAtSnapshot()),
                booking.getStatus(),
                seats.stream().map(BookedSeat::from).toList(),
                booking.getTotalAmount(),
                toSeoul(booking.getExpiresAt()),
                paymentAttempt == null ? null : paymentAttempt.getId(),
                paymentAttempt == null ? null : paymentAttempt.getStatus().name(),
                cancellation == null ? null : cancellation.getId(),
                cancellation == null ? null : cancellation.getStatus().name(),
                toSeoul(booking.getCreatedAt()),
                toSeoul(now)
        );
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}
