package com.tikit.booking.dto.response;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;

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

    // 결제와 취소 정보는 결제 연동(A-07) 이후 채움
    public static BookingResponseDTO of(Booking booking, Long performanceId,
                                        List<BookingSeat> seats, Instant now) {
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
                null,
                null,
                null,
                null,
                toSeoul(booking.getCreatedAt()),
                toSeoul(now)
        );
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}
