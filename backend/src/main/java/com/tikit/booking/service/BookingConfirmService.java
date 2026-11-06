package com.tikit.booking.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.Ticket;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.booking.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingConfirmService {

    private final BookingSeatRepository bookingSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final TicketRepository ticketRepository;

    // 결제 승인 → 예매 확정, 좌석 SOLD, 좌석별 티켓 발급
    // 결제 승인 2차 트랜잭션 안에서만 호출 (예매 행은 호출 쪽에서 이미 잠금, 실패하면 승인까지 함께 롤백)
    // Redis 좌석 락은 따로 풀지 않음. 좌석이 DB에서 SOLD라 다시 선점할 수 없고, 락은 TTL로 풀림
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirm(Booking booking, Instant now) {
        Long bookingId = booking.getId();

        // PENDING_PAYMENT가 아니면 엔티티가 예외
        booking.confirm(now);

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        if (bookingSeats.size() != booking.getSeatCount()) {
            throw new IllegalStateException(
                    "예매 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", seats=" + bookingSeats.size());
        }

        // 일부 좌석만 SOLD가 되면 예매와 좌석 상태가 어긋나므로 전체 롤백
        List<Long> seatIds = bookingSeats.stream()
                .map(BookingSeat::getSessionSeatId)
                .toList();
        int soldSeats = seatHoldRepository.sellSeats(seatIds, bookingId, now);
        if (soldSeats != seatIds.size()) {
            throw new IllegalStateException(
                    "판매 처리된 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", sold=" + soldSeats);
        }

        List<Ticket> tickets = bookingSeats.stream()
                .map(bookingSeat -> Ticket.issue(bookingSeat.getId(), now))
                .toList();
        ticketRepository.saveAll(tickets);
    }
}
