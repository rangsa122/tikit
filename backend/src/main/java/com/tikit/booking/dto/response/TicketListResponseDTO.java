package com.tikit.booking.dto.response;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.Ticket;
import com.tikit.booking.entity.TicketStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record TicketListResponseDTO(List<TicketItem> items) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public record TicketItem(
            Long ticketId,
            Long bookingId,
            Long seatId,
            String seatLabel,
            String performanceTitle,
            OffsetDateTime startsAt,
            TicketStatus status
    ) {

        public static TicketItem of(Ticket ticket, BookingSeat seat, Booking booking) {
            return new TicketItem(
                    ticket.getId(),
                    booking.getId(),
                    seat.getSessionSeatId(),
                    seat.getSeatLabelSnapshot(),
                    booking.getPerformanceTitleSnapshot(),
                    booking.getStartsAtSnapshot().atZone(SEOUL).toOffsetDateTime(),
                    ticket.getStatus()
            );
        }
    }
}
