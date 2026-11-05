package com.tikit.booking.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.Ticket;
import com.tikit.booking.event.SeatHoldReleasedEvent;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.booking.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingCancelService {

    private final BookingSeatRepository bookingSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 환불 성공 → 예매 전체 취소, 좌석 AVAILABLE, 티켓 VOID
    // 환불 결과를 반영하는 트랜잭션 안에서만 호출 (예매 행은 호출 쪽에서 이미 잠금, 실패하면 환불 반영까지 함께 롤백)
    // 환불이 성공으로 확정되기 전에는 호출하지 않음. 그 전까지 좌석과 티켓은 그대로 유지
    // 확정 직후 취소하면 선점 때 잡은 Redis 락이 아직 남아 있을 수 있어 커밋 후 함께 해제
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelConfirmed(Booking booking, Instant now) {
        Long bookingId = booking.getId();

        // 결제 전 예매는 선점 해제 API로 처리하므로 확정 예매만 받음
        if (!booking.isConfirmed()) {
            throw new IllegalStateException(
                    "확정 예매만 전체 취소할 수 있습니다. bookingId=" + bookingId + ", status=" + booking.getStatus());
        }
        booking.cancel(now);

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        if (bookingSeats.size() != booking.getSeatCount()) {
            throw new IllegalStateException(
                    "예매 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", seats=" + bookingSeats.size());
        }

        // 일부 좌석만 풀리면 예매와 좌석 상태가 어긋나므로 전체 롤백
        List<Long> seatIds = bookingSeats.stream()
                .map(BookingSeat::getSessionSeatId)
                .toList();
        int releasedSeats = seatHoldRepository.releaseSoldSeats(seatIds, bookingId, now);
        if (releasedSeats != seatIds.size()) {
            throw new IllegalStateException(
                    "해제된 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", released=" + releasedSeats);
        }

        // 이미 무효인 티켓이 있으면 엔티티가 예외
        List<Ticket> tickets = ticketRepository.findByBookingSeatIds(
                bookingSeats.stream().map(BookingSeat::getId).toList());
        if (tickets.size() != bookingSeats.size()) {
            throw new IllegalStateException(
                    "티켓 수가 맞지 않습니다. bookingId=" + bookingId + ", tickets=" + tickets.size());
        }
        tickets.forEach(ticket -> ticket.voidTicket(now));

        eventPublisher.publishEvent(new SeatHoldReleasedEvent(seatIds, booking.getAdmissionId().toString()));
    }
}
