package com.tikit.booking.service;

import com.tikit.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class BookingExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");

    private BookingRepository bookingRepository;
    private BookingExpiryTxService bookingExpiryTxService;
    private BookingExpiryService service;

    @BeforeEach
    void setup() {
        bookingRepository = mock(BookingRepository.class);
        bookingExpiryTxService = mock(BookingExpiryTxService.class);
        service = new BookingExpiryService(bookingRepository, bookingExpiryTxService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("고정된 시각과 건수 제한으로 대상을 조회한다")
    void expireOverdueBookings_queriesWithNowAndLimit() {
        // given
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        // when
        service.expireOverdueBookings();

        // then
        then(bookingRepository).should().findExpirableIds(eq(NOW),
                eq(BookingExpiryTxService.BLOCKING_ATTEMPT_STATUSES), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(BookingExpiryService.BATCH_SIZE);
    }

    @Test
    @DisplayName("실제로 만료한 건수만 센다")
    void expireOverdueBookings_countsExpiredOnly() {
        // given
        givenTargets(1L, 2L);
        given(bookingExpiryTxService.expire(1L, NOW)).willReturn(true);
        given(bookingExpiryTxService.expire(2L, NOW)).willReturn(false);

        // when
        int expiredCount = service.expireOverdueBookings();

        // then
        assertThat(expiredCount).isEqualTo(1);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 예매를 계속 처리한다")
    void expireOverdueBookings_continuesAfterFailure() {
        // given
        givenTargets(1L, 2L);
        given(bookingExpiryTxService.expire(1L, NOW)).willThrow(new IllegalStateException("좌석 수 불일치"));
        given(bookingExpiryTxService.expire(2L, NOW)).willReturn(true);

        // when
        int expiredCount = service.expireOverdueBookings();

        // then
        assertThat(expiredCount).isEqualTo(1);
        then(bookingExpiryTxService).should().expire(2L, NOW);
    }

    private void givenTargets(Long... bookingIds) {
        given(bookingRepository.findExpirableIds(eq(NOW), any(), any(Pageable.class)))
                .willReturn(List.of(bookingIds));
    }
}
