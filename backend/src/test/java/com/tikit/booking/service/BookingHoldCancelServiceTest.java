package com.tikit.booking.service;

import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.event.SeatHoldReleasedEvent;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.common.ErrorCode;
import com.tikit.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BookingHoldCancelServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long BOOKING_ID = 100L;
    private static final Long SESSION_ID = 10L;
    private static final List<Long> SEAT_IDS = List.of(101L, 102L);
    private static final UUID ADMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-20T01:03:00Z");

    private BookingRepository bookingRepository;
    private SeatHoldRepository seatHoldRepository;
    private PaymentAttemptRepository paymentAttemptRepository;
    private ApplicationEventPublisher eventPublisher;
    private BookingHoldCancelService service;
    private Booking booking;

    @BeforeEach
    void setup() {
        bookingRepository = mock(BookingRepository.class);
        BookingSeatRepository bookingSeatRepository = mock(BookingSeatRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new BookingHoldCancelService(bookingRepository, bookingSeatRepository, seatHoldRepository,
                paymentAttemptRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

        booking = Booking.create(MEMBER_ID, SESSION_ID, ADMISSION_ID, 2, 100_000L, "테스트 공연",
                NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofHours(12)),
                "v1", "{}", NOW.plus(Duration.ofMinutes(2)));
        ReflectionTestUtils.setField(booking, "id", BOOKING_ID);

        given(bookingRepository.findByIdForUpdate(BOOKING_ID)).willReturn(Optional.of(booking));
        given(bookingSeatRepository.findByBookingId(BOOKING_ID)).willReturn(List.of(
                BookingSeat.create(BOOKING_ID, SESSION_ID, 101L, "A-1", "R", 50_000L),
                BookingSeat.create(BOOKING_ID, SESSION_ID, 102L, "A-2", "R", 50_000L)));
        given(seatHoldRepository.findSessionWithPerformance(SESSION_ID)).willReturn(Optional.empty());
        given(seatHoldRepository.releaseSeats(SEAT_IDS, BOOKING_ID, NOW)).willReturn(2);
    }

    @Test
    @DisplayName("결제 대기 예매의 좌석을 모두 해제하고 취소 상태로 바꾼 뒤 Redis 락 해제 이벤트를 발행한다")
    void cancelHold_success() {
        // when
        BookingResponseDTO response = service.cancelHold(MEMBER_ID, BOOKING_ID);

        // then
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(booking.getCanceledAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELED);
        assertThat(response.seats()).hasSize(2);
        then(seatHoldRepository).should().releaseSeats(SEAT_IDS, BOOKING_ID, NOW);
        then(eventPublisher).should().publishEvent(new SeatHoldReleasedEvent(SEAT_IDS, ADMISSION_ID.toString()));
    }

    @Test
    @DisplayName("없는 예매면 404")
    void cancelHold_notFound() {
        // given
        given(bookingRepository.findByIdForUpdate(BOOKING_ID)).willReturn(Optional.empty());

        // when, then
        assertBookingException(MEMBER_ID, ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 회원의 예매면 없는 예매와 같은 404")
    void cancelHold_otherMember() {
        assertBookingException(OTHER_MEMBER_ID, ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("이미 만료된 예매는 좌석을 건드리지 않고 현재 상태를 반환한다")
    void cancelHold_alreadyExpired() {
        // given
        booking.expire();

        // when
        BookingResponseDTO response = service.cancelHold(MEMBER_ID, BOOKING_ID);

        // then
        assertThat(response.status()).isEqualTo(BookingStatus.EXPIRED);
        assertNothingReleased();
    }

    @Test
    @DisplayName("이미 취소된 예매는 좌석을 건드리지 않고 현재 상태를 반환한다")
    void cancelHold_alreadyCanceled() {
        // given
        booking.cancel(NOW.minusSeconds(30));

        // when
        BookingResponseDTO response = service.cancelHold(MEMBER_ID, BOOKING_ID);

        // then
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELED);
        assertThat(booking.getCanceledAt()).isEqualTo(NOW.minusSeconds(30));
        assertNothingReleased();
    }

    @Test
    @DisplayName("확정된 예매는 전체 취소 API를 쓰도록 409")
    void cancelHold_confirmed() {
        // given
        booking.confirm(NOW.minusSeconds(30));

        // when, then
        assertBookingException(MEMBER_ID, ErrorCode.BOOKING_ALREADY_CONFIRMED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("결제 처리 중이거나 결과 확인 중이면 409")
    void cancelHold_paymentInProgress() {
        // given
        given(paymentAttemptRepository.existsByBookingIdAndStatusIn(
                BOOKING_ID, BookingExpiryTxService.BLOCKING_ATTEMPT_STATUSES)).willReturn(true);

        // when, then
        assertBookingException(MEMBER_ID, ErrorCode.PAYMENT_IN_PROGRESS);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("일부 좌석만 해제되면 예외를 던지고 이벤트를 발행하지 않는다")
    void cancelHold_partialRelease() {
        // given
        given(seatHoldRepository.releaseSeats(SEAT_IDS, BOOKING_ID, NOW)).willReturn(1);

        // when, then
        assertThatThrownBy(() -> service.cancelHold(MEMBER_ID, BOOKING_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("해제된 좌석 수");
        then(eventPublisher).should(never()).publishEvent(any());
    }

    private void assertBookingException(Long memberId, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.cancelHold(memberId, BOOKING_ID))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
        assertNothingReleased();
    }

    private void assertNothingReleased() {
        then(seatHoldRepository).should(never()).releaseSeats(any(), any(), any());
        then(eventPublisher).should(never()).publishEvent(any());
    }
}
