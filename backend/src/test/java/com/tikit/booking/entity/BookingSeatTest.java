package com.tikit.booking.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingSeatTest {

    @Test
    @DisplayName("예매 좌석은 예매 시점의 좌석 정보와 가격으로 생성된다")
    void create() {
        BookingSeat bookingSeat = BookingSeat.create(1L, 10L, 100L, "A열 12번", "VIP", 150_000L);

        assertThat(bookingSeat.getBookingId()).isEqualTo(1L);
        assertThat(bookingSeat.getSessionId()).isEqualTo(10L);
        assertThat(bookingSeat.getSessionSeatId()).isEqualTo(100L);
        assertThat(bookingSeat.getSeatLabelSnapshot()).isEqualTo("A열 12번");
        assertThat(bookingSeat.getGradeSnapshot()).isEqualTo("VIP");
        assertThat(bookingSeat.getPriceSnapshot()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("필수 ID가 없거나 가격이 음수면 생성할 수 없다")
    void createInvalid() {
        assertThatThrownBy(() -> BookingSeat.create(null, 10L, 100L, "A열 12번", "VIP", 150_000L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BookingSeat.create(1L, 10L, 100L, "A열 12번", "VIP", -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
