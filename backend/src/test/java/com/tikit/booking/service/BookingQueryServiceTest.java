package com.tikit.booking.service;

import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.dto.response.TicketListResponseDTO;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.Ticket;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.booking.repository.TicketRepository;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.PageResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BookingQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long PERFORMANCE_ID = 5L;
    private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");

    private BookingRepository bookingRepository;
    private BookingSeatRepository bookingSeatRepository;
    private SeatHoldRepository seatHoldRepository;
    private TicketRepository ticketRepository;
    private PaymentAttemptRepository paymentAttemptRepository;
    private CancellationRepository cancellationRepository;
    private BookingQueryService bookingQueryService;

    @BeforeEach
    void setup() {
        bookingRepository = mock(BookingRepository.class);
        bookingSeatRepository = mock(BookingSeatRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        ticketRepository = mock(TicketRepository.class);
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        cancellationRepository = mock(CancellationRepository.class);
        bookingQueryService = new BookingQueryService(
                bookingRepository,
                bookingSeatRepository,
                seatHoldRepository,
                ticketRepository,
                paymentAttemptRepository,
                cancellationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        PerformanceSession session = mock(PerformanceSession.class);
        Performance performance = mock(Performance.class);
        given(session.getId()).willReturn(SESSION_ID);
        given(session.getPerformance()).willReturn(performance);
        given(performance.getId()).willReturn(PERFORMANCE_ID);
        given(seatHoldRepository.findSessionsWithPerformance(anyCollection())).willReturn(List.of(session));
    }

    @Test
    @DisplayName("예매 상세는 좌석과 공연 ID를 담고, 결제가 없으면 결제와 취소 정보는 null이다")
    void getBooking_withoutPayment() {
        // given
        given(bookingRepository.findByIdAndMemberId(100L, MEMBER_ID)).willReturn(Optional.of(booking(100L)));
        given(bookingSeatRepository.findByBookingIdInOrderById(List.of(100L)))
                .willReturn(List.of(seat(1L, 100L, 201L, "A열 1번")));

        // when
        BookingResponseDTO response = bookingQueryService.getBooking(MEMBER_ID, 100L);

        // then
        assertThat(response.bookingId()).isEqualTo(100L);
        assertThat(response.performanceId()).isEqualTo(PERFORMANCE_ID);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(response.seats()).hasSize(1);
        assertThat(response.seats().getFirst().seatId()).isEqualTo(201L);
        assertThat(response.seats().getFirst().label()).isEqualTo("A열 1번");
        assertThat(response.paymentAttemptId()).isNull();
        assertThat(response.paymentStatus()).isNull();
        assertThat(response.cancellationId()).isNull();
        assertThat(response.cancellationStatus()).isNull();
        assertThat(response.serverTime().toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("결제 시도가 여러 번이면 최신 시도와 그 취소 상태를 돌려준다")
    void getBooking_latestPaymentAndCancellation() {
        // given
        given(bookingRepository.findByIdAndMemberId(100L, MEMBER_ID)).willReturn(Optional.of(booking(100L)));
        List<PaymentAttempt> attempts = List.of(
                attempt(31L, 100L, PaymentAttemptStatus.APPROVED),
                attempt(30L, 100L, PaymentAttemptStatus.FAILED));
        List<Cancellation> cancellations = List.of(
                cancellation(41L, 31L, CancellationStatus.SUCCEEDED),
                cancellation(40L, 31L, CancellationStatus.FAILED));
        given(paymentAttemptRepository.findByBookingIdInOrderByIdDesc(List.of(100L))).willReturn(attempts);
        given(cancellationRepository.findByPaymentAttemptIdInOrderByIdDesc(List.of(31L))).willReturn(cancellations);

        // when
        BookingResponseDTO response = bookingQueryService.getBooking(MEMBER_ID, 100L);

        // then
        assertThat(response.paymentAttemptId()).isEqualTo(31L);
        assertThat(response.paymentStatus()).isEqualTo("APPROVED");
        assertThat(response.cancellationId()).isEqualTo(41L);
        assertThat(response.cancellationStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("다른 회원 예매나 없는 예매는 404다")
    void getBooking_notOwned() {
        // given
        given(bookingRepository.findByIdAndMemberId(100L, MEMBER_ID)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> bookingQueryService.getBooking(MEMBER_ID, 100L))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("목록은 최신 생성순, 동률은 bookingId 내림차순으로 조회하고 예매별로 좌석을 나눈다")
    void getMyBookings_sortedAndGrouped() {
        // given
        Page<Booking> page = new PageImpl<>(List.of(booking(101L), booking(100L)), PageRequest.of(1, 2), 5);
        given(bookingRepository.findByMemberId(eq(MEMBER_ID), any(Pageable.class))).willReturn(page);
        given(bookingSeatRepository.findByBookingIdInOrderById(List.of(101L, 100L))).willReturn(List.of(
                seat(1L, 100L, 201L, "A열 1번"),
                seat(2L, 101L, 202L, "A열 2번"),
                seat(3L, 101L, 203L, "A열 3번")));

        // when
        PageResponseDTO<BookingResponseDTO> result = bookingQueryService.getMyBookings(MEMBER_ID, null, 1, 2);

        // then
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(bookingRepository).should().findByMemberId(eq(MEMBER_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        assertThat(result.getItems()).extracting(BookingResponseDTO::bookingId).containsExactly(101L, 100L);
        assertThat(result.getItems().get(0).seats()).hasSize(2);
        assertThat(result.getItems().get(1).seats()).hasSize(1);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("status를 주면 그 상태만 조회한다")
    void getMyBookings_statusFilter() {
        // given
        given(bookingRepository.findByMemberIdAndStatus(eq(MEMBER_ID), eq(BookingStatus.CONFIRMED), any(Pageable.class)))
                .willReturn(Page.empty());

        // when
        bookingQueryService.getMyBookings(MEMBER_ID, BookingStatus.CONFIRMED, 0, 20);

        // then
        then(bookingRepository).should(never()).findByMemberId(any(), any());
    }

    @Test
    @DisplayName("예매가 없으면 빈 목록이고 좌석과 결제는 조회하지 않는다")
    void getMyBookings_empty() {
        // given
        given(bookingRepository.findByMemberId(eq(MEMBER_ID), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when
        PageResponseDTO<BookingResponseDTO> result = bookingQueryService.getMyBookings(MEMBER_ID, null, 0, 20);

        // then
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalPages()).isZero();
        then(bookingSeatRepository).shouldHaveNoInteractions();
        then(paymentAttemptRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("티켓은 좌석 순서대로 좌석명과 공연 정보를 담아 돌려준다")
    void getTickets_valid() {
        // given
        given(bookingRepository.findByIdAndMemberId(100L, MEMBER_ID)).willReturn(Optional.of(booking(100L)));
        given(bookingSeatRepository.findByBookingIdInOrderById(List.of(100L))).willReturn(List.of(
                seat(1L, 100L, 201L, "A열 1번"),
                seat(2L, 100L, 202L, "A열 2번")));
        given(ticketRepository.findByBookingSeatIds(List.of(1L, 2L))).willReturn(List.of(
                ticket(52L, 2L, false),
                ticket(51L, 1L, true)));

        // when
        TicketListResponseDTO result = bookingQueryService.getTickets(MEMBER_ID, 100L);

        // then
        assertThat(result.items()).extracting(TicketListResponseDTO.TicketItem::ticketId).containsExactly(51L, 52L);
        TicketListResponseDTO.TicketItem first = result.items().getFirst();
        assertThat(first.bookingId()).isEqualTo(100L);
        assertThat(first.seatId()).isEqualTo(201L);
        assertThat(first.seatLabel()).isEqualTo("A열 1번");
        assertThat(first.performanceTitle()).isEqualTo("테스트 공연");
        assertThat(first.status()).isEqualTo(TicketStatus.VOID);
        assertThat(result.items().get(1).status()).isEqualTo(TicketStatus.VALID);
    }

    @Test
    @DisplayName("미확정 예매는 티켓이 빈 배열이다")
    void getTickets_notConfirmed() {
        // given
        given(bookingRepository.findByIdAndMemberId(100L, MEMBER_ID)).willReturn(Optional.of(booking(100L)));
        given(bookingSeatRepository.findByBookingIdInOrderById(List.of(100L)))
                .willReturn(List.of(seat(1L, 100L, 201L, "A열 1번")));
        given(ticketRepository.findByBookingSeatIds(List.of(1L))).willReturn(List.of());

        // when, then
        assertThat(bookingQueryService.getTickets(MEMBER_ID, 100L).items()).isEmpty();
    }

    @Test
    @DisplayName("다른 회원 예매의 티켓은 404다")
    void getTickets_notOwned() {
        // given
        given(bookingRepository.findByIdAndMemberId(100L, MEMBER_ID)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> bookingQueryService.getTickets(MEMBER_ID, 100L))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        then(ticketRepository).shouldHaveNoInteractions();
    }

    private Booking booking(Long id) {
        Booking booking = Booking.create(MEMBER_ID, SESSION_ID, UUID.randomUUID(), 1, 50_000L, "테스트 공연",
                NOW.plus(Duration.ofDays(7)), NOW.plus(Duration.ofDays(5)), "v1", "{}",
                NOW.plus(Duration.ofMinutes(5)));
        ReflectionTestUtils.setField(booking, "id", id);
        ReflectionTestUtils.setField(booking, "createdAt", NOW);
        return booking;
    }

    private BookingSeat seat(Long id, Long bookingId, Long sessionSeatId, String label) {
        BookingSeat seat = BookingSeat.create(bookingId, SESSION_ID, sessionSeatId, label, "R", 50_000L);
        ReflectionTestUtils.setField(seat, "id", id);
        return seat;
    }

    private Ticket ticket(Long id, Long bookingSeatId, boolean voided) {
        Ticket ticket = Ticket.issue(bookingSeatId, NOW);
        ReflectionTestUtils.setField(ticket, "id", id);
        if (voided) {
            ticket.voidTicket(NOW);
        }
        return ticket;
    }

    private PaymentAttempt attempt(Long id, Long bookingId, PaymentAttemptStatus status) {
        PaymentAttempt attempt = mock(PaymentAttempt.class);
        given(attempt.getId()).willReturn(id);
        given(attempt.getBookingId()).willReturn(bookingId);
        given(attempt.getStatus()).willReturn(status);
        return attempt;
    }

    private Cancellation cancellation(Long id, Long paymentAttemptId, CancellationStatus status) {
        Cancellation cancellation = mock(Cancellation.class);
        given(cancellation.getId()).willReturn(id);
        given(cancellation.getPaymentAttemptId()).willReturn(paymentAttemptId);
        given(cancellation.getStatus()).willReturn(status);
        return cancellation;
    }
}
