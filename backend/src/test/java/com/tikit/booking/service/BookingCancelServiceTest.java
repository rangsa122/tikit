package com.tikit.booking.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.Ticket;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.booking.event.SeatHoldReleasedEvent;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.booking.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BookingCancelServiceTest {

    private static final Long BOOKING_ID = 100L;
    private static final Long SESSION_ID = 10L;
    private static final List<Long> SEAT_IDS = List.of(101L, 102L);
    private static final List<Long> BOOKING_SEAT_IDS = List.of(1001L, 1002L);
    private static final UUID ADMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-20T01:03:00Z");
    private static final Instant NOW = Instant.parse("2026-11-21T09:00:00Z");

    private SeatHoldRepository seatHoldRepository;
    private TicketRepository ticketRepository;
    private ApplicationEventPublisher eventPublisher;
    private BookingCancelService service;
    private Booking booking;
    private List<Ticket> tickets;

    @BeforeEach
    void setup() {
        BookingSeatRepository bookingSeatRepository = mock(BookingSeatRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        ticketRepository = mock(TicketRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new BookingCancelService(bookingSeatRepository, seatHoldRepository, ticketRepository,
                eventPublisher);

        booking = Booking.create(1L, SESSION_ID, ADMISSION_ID, 2, 100_000L, "테스트 공연",
                NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofHours(12)),
                "v1", "{}", CONFIRMED_AT.plus(Duration.ofMinutes(2)));
        ReflectionTestUtils.setField(booking, "id", BOOKING_ID);
        booking.confirm(CONFIRMED_AT);

        BookingSeat first = BookingSeat.create(BOOKING_ID, SESSION_ID, 101L, "A-1", "R", 50_000L);
        BookingSeat second = BookingSeat.create(BOOKING_ID, SESSION_ID, 102L, "A-2", "R", 50_000L);
        ReflectionTestUtils.setField(first, "id", 1001L);
        ReflectionTestUtils.setField(second, "id", 1002L);
        tickets = List.of(Ticket.issue(1001L, CONFIRMED_AT), Ticket.issue(1002L, CONFIRMED_AT));

        given(bookingSeatRepository.findByBookingId(BOOKING_ID)).willReturn(List.of(first, second));
        given(seatHoldRepository.releaseSoldSeats(SEAT_IDS, BOOKING_ID, NOW)).willReturn(2);
        given(ticketRepository.findByBookingSeatIds(BOOKING_SEAT_IDS)).willReturn(tickets);
    }

    @Test
    @DisplayName("확정 예매를 취소하고 판매된 좌석을 되돌린 뒤 티켓을 모두 무효 처리하고 Redis 락 해제 이벤트를 발행한다")
    void cancelConfirmed_success() {
        // when
        service.cancelConfirmed(booking, NOW);

        // then
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(booking.getCanceledAt()).isEqualTo(NOW);
        then(seatHoldRepository).should().releaseSoldSeats(SEAT_IDS, BOOKING_ID, NOW);
        assertThat(tickets)
                .extracting(Ticket::getBookingSeatId, Ticket::getStatus, Ticket::getVoidedAt)
                .containsExactly(
                        tuple(1001L, TicketStatus.VOID, NOW),
                        tuple(1002L, TicketStatus.VOID, NOW));
        then(eventPublisher).should().publishEvent(new SeatHoldReleasedEvent(SEAT_IDS, ADMISSION_ID.toString()));
    }

    @Test
    @DisplayName("결제 대기 예매는 전체 취소하지 않고 좌석과 티켓을 건드리지 않는다")
    void cancelConfirmed_pendingPayment() {
        // given
        Booking pending = Booking.create(1L, SESSION_ID, UUID.randomUUID(), 2, 100_000L, "테스트 공연",
                NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofHours(12)),
                "v1", "{}", NOW.plus(Duration.ofMinutes(2)));
        ReflectionTestUtils.setField(pending, "id", BOOKING_ID);

        // when, then
        assertThatThrownBy(() -> service.cancelConfirmed(pending, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("확정 예매만");
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertNothingChanged();
    }

    @Test
    @DisplayName("이미 취소된 예매는 다시 취소하지 않는다")
    void cancelConfirmed_alreadyCanceled() {
        // given
        booking.cancel(NOW.minusSeconds(30));

        // when, then
        assertThatThrownBy(() -> service.cancelConfirmed(booking, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(booking.getCanceledAt()).isEqualTo(NOW.minusSeconds(30));
        assertNothingChanged();
    }

    @Test
    @DisplayName("일부 좌석만 되돌려지면 예외를 던지고 티켓을 무효 처리하지 않는다")
    void cancelConfirmed_partialRelease() {
        // given
        given(seatHoldRepository.releaseSoldSeats(SEAT_IDS, BOOKING_ID, NOW)).willReturn(1);

        // when, then
        assertThatThrownBy(() -> service.cancelConfirmed(booking, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("해제된 좌석 수");
        then(ticketRepository).should(never()).findByBookingSeatIds(any());
        assertThat(tickets).extracting(Ticket::getStatus).containsOnly(TicketStatus.VALID);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("티켓 수가 좌석 수와 다르면 예외를 던진다")
    void cancelConfirmed_ticketCountMismatch() {
        // given
        given(ticketRepository.findByBookingSeatIds(BOOKING_SEAT_IDS)).willReturn(List.of(tickets.get(0)));

        // when, then
        assertThatThrownBy(() -> service.cancelConfirmed(booking, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("티켓 수");
        assertThat(tickets.get(0).getStatus()).isEqualTo(TicketStatus.VALID);
    }

    @Test
    @DisplayName("이미 무효인 티켓이 있으면 예외를 던진다")
    void cancelConfirmed_ticketAlreadyVoid() {
        // given
        tickets.get(1).voidTicket(NOW.minusSeconds(30));

        // when, then
        assertThatThrownBy(() -> service.cancelConfirmed(booking, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("무효");
    }

    private void assertNothingChanged() {
        then(seatHoldRepository).should(never()).releaseSoldSeats(any(), any(), any());
        then(eventPublisher).should(never()).publishEvent(any());
        assertThat(tickets).extracting(Ticket::getStatus).containsOnly(TicketStatus.VALID);
    }
}
