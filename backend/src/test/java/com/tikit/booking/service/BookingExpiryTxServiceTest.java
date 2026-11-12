package com.tikit.booking.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.event.SeatHoldReleasedEvent;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BookingExpiryTxServiceTest {

    private static final Long BOOKING_ID = 100L;
    private static final Long SESSION_ID = 10L;
    private static final List<Long> SEAT_IDS = List.of(101L, 102L);
    private static final UUID ADMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-20T01:05:00Z");

    private BookingRepository bookingRepository;
    private BookingSeatRepository bookingSeatRepository;
    private SeatHoldRepository seatHoldRepository;
    private PaymentAttemptRepository paymentAttemptRepository;
    private ApplicationEventPublisher eventPublisher;
    private BookingExpiryTxService service;
    private Booking booking;

    @BeforeEach
    void setup() {
        bookingRepository = mock(BookingRepository.class);
        bookingSeatRepository = mock(BookingSeatRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new BookingExpiryTxService(bookingRepository, bookingSeatRepository,
                seatHoldRepository, paymentAttemptRepository, eventPublisher);

        booking = Booking.create(1L, SESSION_ID, ADMISSION_ID, 2, 100_000L, "테스트 공연",
                EXPIRES_AT.plus(Duration.ofDays(1)), EXPIRES_AT.plus(Duration.ofHours(12)),
                "v1", "{}", EXPIRES_AT);
        ReflectionTestUtils.setField(booking, "id", BOOKING_ID);

        given(bookingRepository.findByIdForUpdate(BOOKING_ID)).willReturn(Optional.of(booking));
        given(bookingSeatRepository.findByBookingId(BOOKING_ID)).willReturn(List.of(
                BookingSeat.create(BOOKING_ID, SESSION_ID, 101L, "A-1", "R", 50_000L),
                BookingSeat.create(BOOKING_ID, SESSION_ID, 102L, "A-2", "R", 50_000L)));
        given(seatHoldRepository.releaseSeats(SEAT_IDS, BOOKING_ID, EXPIRES_AT)).willReturn(2);
    }

    @Test
    @DisplayName("기한 시각 정각이면 만료하고 좌석을 해제한 뒤 락 해제 이벤트를 발행한다")
    void expire_exactlyAtDeadline() {
        // when
        boolean expired = service.expire(BOOKING_ID, EXPIRES_AT);

        // then
        assertThat(expired).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        then(seatHoldRepository).should().releaseSeats(SEAT_IDS, BOOKING_ID, EXPIRES_AT);
        then(eventPublisher).should().publishEvent(new SeatHoldReleasedEvent(SEAT_IDS, ADMISSION_ID.toString()));
    }

    @Test
    @DisplayName("기한 1초 전이면 만료하지 않는다")
    void expire_oneSecondBeforeDeadline() {
        // when
        boolean expired = service.expire(BOOKING_ID, EXPIRES_AT.minusSeconds(1));

        // then
        assertThat(expired).isFalse();
        assertUntouched();
    }

    @Test
    @DisplayName("결제 진행 중이거나 승인된 결제가 있으면 기한이 지나도 만료하지 않는다")
    void expire_blockedByPayment() {
        // given
        given(paymentAttemptRepository.existsByBookingIdAndStatusIn(
                BOOKING_ID, BookingExpiryTxService.BLOCKING_ATTEMPT_STATUSES)).willReturn(true);

        // when
        boolean expired = service.expire(BOOKING_ID, EXPIRES_AT);

        // then
        assertThat(expired).isFalse();
        assertUntouched();
    }

    @Test
    @DisplayName("이미 만료된 예매는 다시 처리하지 않는다")
    void expire_alreadyExpired() {
        // given
        booking.expire();

        // when
        boolean expired = service.expire(BOOKING_ID, EXPIRES_AT);

        // then
        assertThat(expired).isFalse();
        then(seatHoldRepository).should(never()).releaseSeats(any(), any(), any());
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("확정된 예매는 기한이 지나도 만료하지 않는다")
    void expire_confirmed() {
        // given
        booking.confirm(EXPIRES_AT.minusSeconds(10));

        // when
        boolean expired = service.expire(BOOKING_ID, EXPIRES_AT);

        // then
        assertThat(expired).isFalse();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("없는 예매면 아무것도 하지 않는다")
    void expire_notFound() {
        // given
        given(bookingRepository.findByIdForUpdate(BOOKING_ID)).willReturn(Optional.empty());

        // when, then
        assertThat(service.expire(BOOKING_ID, EXPIRES_AT)).isFalse();
    }

    @Test
    @DisplayName("예매 좌석 수가 맞지 않으면 좌석을 건드리지 않고 예외를 던진다")
    void expire_seatCountMismatch() {
        // given
        given(bookingSeatRepository.findByBookingId(BOOKING_ID)).willReturn(List.of(
                BookingSeat.create(BOOKING_ID, SESSION_ID, 101L, "A-1", "R", 50_000L)));

        // when, then
        assertThatThrownBy(() -> service.expire(BOOKING_ID, EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class);
        assertUntouched();
    }

    @Test
    @DisplayName("좌석이 일부만 해제되면 예외를 던져 롤백시키고 이벤트를 발행하지 않는다")
    void expire_partialRelease() {
        // given
        given(seatHoldRepository.releaseSeats(SEAT_IDS, BOOKING_ID, EXPIRES_AT)).willReturn(1);

        // when, then
        assertThatThrownBy(() -> service.expire(BOOKING_ID, EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("결제 확인은 차단 상태 목록으로 조회한다")
    void expire_checksBlockingStatuses() {
        // when
        service.expire(BOOKING_ID, EXPIRES_AT);

        // then
        then(paymentAttemptRepository).should().existsByBookingIdAndStatusIn(any(), anyCollection());
        assertThat(BookingExpiryTxService.BLOCKING_ATTEMPT_STATUSES).extracting(Enum::name)
                .containsExactlyInAnyOrder("PROCESSING", "RECONCILING", "APPROVED");
    }

    private void assertUntouched() {
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        then(seatHoldRepository).should(never()).releaseSeats(any(), any(), any());
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }
}
