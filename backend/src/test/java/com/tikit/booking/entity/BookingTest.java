package com.tikit.booking.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private static final Instant NOW = Instant.parse("2026-11-02T01:00:00Z");

    private Booking pendingBooking() {
        return Booking.create(
                1L, 10L, UUID.randomUUID(), 2, 110_000L,
                "테스트 공연",
                NOW.plus(Duration.ofDays(7)),
                NOW.plus(Duration.ofDays(6)),
                "v1",
                "{\"refundRules\":[]}",
                NOW.plus(Duration.ofMinutes(10))
        );
    }

    @Test
    @DisplayName("임시 예매는 PENDING_PAYMENT 상태로 생성되는지 확인")
    void create() {
        Booking booking = pendingBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getSeatCount()).isEqualTo((short) 2);
        assertThat(booking.isPendingPayment()).isTrue();
        assertThat(booking.getConfirmedAt()).isNull();
        assertThat(booking.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("좌석 수가 0 이하이거나 결제 기한이 없으면 생성할 수 없다")
    void createInvalid() {
        assertThatThrownBy(() -> Booking.create(1L, 10L, UUID.randomUUID(), 0, 0L,
                "공연", NOW, NOW, "v1", "{}", NOW.plusSeconds(600)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Booking.create(1L, 10L, UUID.randomUUID(), 1, 0L,
                "공연", NOW, NOW, "v1", "{}", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결제 기한 시각부터 만료로 판단한다")
    void isExpired() {
        Booking booking = pendingBooking();

        assertThat(booking.isExpired(NOW)).isFalse();
        assertThat(booking.isExpired(booking.getExpiresAt())).isTrue();
    }

    @Test
    @DisplayName("결제 대기 예매를 확정하면 CONFIRMED가 되고 확정 시각이 기록")
    void confirm() {
        Booking booking = pendingBooking();

        booking.confirm(NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("결제 대기가 아니면 확정하거나 만료할 수 없다")
    void confirmOrExpireFromInvalidState() {
        Booking expired = pendingBooking();
        expired.expire();
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThatThrownBy(() -> expired.confirm(NOW)).isInstanceOf(IllegalStateException.class);

        Booking confirmed = pendingBooking();
        confirmed.confirm(NOW);
        assertThatThrownBy(confirmed::expire).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결제 대기와 확정 예매는 취소할 수 있다")
    void cancel() {
        Booking pending = pendingBooking();
        pending.cancel(NOW);
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(pending.getCanceledAt()).isEqualTo(NOW);

        Booking confirmed = pendingBooking();
        confirmed.confirm(NOW);
        confirmed.cancel(NOW);
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CANCELED);
    }

    @Test
    @DisplayName("만료되거나 이미 취소된 예매는 취소할 수 없다")
    void cancelFromInvalidState() {
        Booking expired = pendingBooking();
        expired.expire();
        assertThatThrownBy(() -> expired.cancel(NOW)).isInstanceOf(IllegalStateException.class);

        Booking canceled = pendingBooking();
        canceled.cancel(NOW);
        assertThatThrownBy(() -> canceled.cancel(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("본인 예매인지 확인한다")
    void isOwnedBy() {
        Booking booking = pendingBooking();

        assertThat(booking.isOwnedBy(1L)).isTrue();
        assertThat(booking.isOwnedBy(2L)).isFalse();
    }
}
