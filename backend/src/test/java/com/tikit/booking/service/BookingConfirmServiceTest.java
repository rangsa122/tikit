package com.tikit.booking.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.Ticket;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.booking.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BookingConfirmServiceTest {

    private static final Long BOOKING_ID = 100L;
    private static final Long SESSION_ID = 10L;
    private static final List<Long> SEAT_IDS = List.of(101L, 102L);
    private static final UUID ADMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-20T01:03:00Z");

    private BookingSeatRepository bookingSeatRepository;
    private SeatHoldRepository seatHoldRepository;
    private TicketRepository ticketRepository;
    private BookingConfirmService service;
    private Booking booking;

    @BeforeEach
    void setup() {
        bookingSeatRepository = mock(BookingSeatRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        ticketRepository = mock(TicketRepository.class);
        service = new BookingConfirmService(bookingSeatRepository, seatHoldRepository, ticketRepository);

        booking = Booking.create(1L, SESSION_ID, ADMISSION_ID, 2, 100_000L, "테스트 공연",
                NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofHours(12)),
                "v1", "{}", NOW.plus(Duration.ofMinutes(2)));
        ReflectionTestUtils.setField(booking, "id", BOOKING_ID);

        BookingSeat first = BookingSeat.create(BOOKING_ID, SESSION_ID, 101L, "A-1", "R", 50_000L);
        BookingSeat second = BookingSeat.create(BOOKING_ID, SESSION_ID, 102L, "A-2", "R", 50_000L);
        ReflectionTestUtils.setField(first, "id", 1001L);
        ReflectionTestUtils.setField(second, "id", 1002L);
        given(bookingSeatRepository.findByBookingId(BOOKING_ID)).willReturn(List.of(first, second));
        given(seatHoldRepository.sellSeats(SEAT_IDS, BOOKING_ID, NOW)).willReturn(2);
    }

    @Test
    @DisplayName("예매를 확정하고 좌석을 판매 처리한 뒤 좌석마다 티켓을 발급한다")
    void confirm_success() {
        // when
        service.confirm(booking, NOW);

        // then
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmedAt()).isEqualTo(NOW);
        then(seatHoldRepository).should().sellSeats(SEAT_IDS, BOOKING_ID, NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        then(ticketRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(Ticket::getBookingSeatId, Ticket::getStatus, Ticket::getIssuedAt)
                .containsExactly(
                        tuple(1001L, TicketStatus.VALID, NOW),
                        tuple(1002L, TicketStatus.VALID, NOW));
    }

    @Test
    @DisplayName("결제 대기 상태가 아니면 확정하지 않고 좌석과 티켓을 건드리지 않는다")
    void confirm_notPendingPayment() {
        // given
        booking.expire();

        // when, then
        assertThatThrownBy(() -> service.confirm(booking, NOW))
                .isInstanceOf(IllegalStateException.class);
        then(seatHoldRepository).should(never()).sellSeats(any(), any(), any());
        then(ticketRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("예매 좌석 수가 예매 매수와 다르면 예외를 던진다")
    void confirm_bookingSeatCountMismatch() {
        // given
        given(bookingSeatRepository.findByBookingId(BOOKING_ID)).willReturn(List.of(
                BookingSeat.create(BOOKING_ID, SESSION_ID, 101L, "A-1", "R", 50_000L)));

        // when, then
        assertThatThrownBy(() -> service.confirm(booking, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("예매 좌석 수");
        then(seatHoldRepository).should(never()).sellSeats(any(), any(), any());
        then(ticketRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("일부 좌석만 판매 처리되면 예외를 던지고 티켓을 발급하지 않는다")
    void confirm_partialSold() {
        // given
        given(seatHoldRepository.sellSeats(SEAT_IDS, BOOKING_ID, NOW)).willReturn(1);

        // when, then
        assertThatThrownBy(() -> service.confirm(booking, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("판매 처리된 좌석 수");
        then(ticketRepository).should(never()).saveAll(anyList());
    }
}
