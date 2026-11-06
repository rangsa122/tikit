package com.tikit.booking.event;

import com.tikit.booking.redis.SeatLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class SeatLockReleaseListenerTest {

    private static final List<Long> SEAT_IDS = List.of(101L, 102L);
    private static final String LOCK_TOKEN = "lock-token";

    private SeatLockRepository seatLockRepository;
    private SeatLockReleaseListener listener;

    @BeforeEach
    void setup() {
        seatLockRepository = mock(SeatLockRepository.class);
        listener = new SeatLockReleaseListener(seatLockRepository);
    }

    @Test
    @DisplayName("선점 해제 이벤트를 받으면 해당 좌석과 토큰으로 Redis 락을 푼다")
    void onSeatHoldReleased_unlocks() {
        // when
        listener.onSeatHoldReleased(new SeatHoldReleasedEvent(SEAT_IDS, LOCK_TOKEN));

        // then
        then(seatLockRepository).should().unlockAll(SEAT_IDS, LOCK_TOKEN);
    }

    @Test
    @DisplayName("Redis 장애로 락 해제에 실패해도 예외를 밖으로 내지 않는다")
    void onSeatHoldReleased_redisDown() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(seatLockRepository).unlockAll(SEAT_IDS, LOCK_TOKEN);

        // when, then
        assertThatCode(() -> listener.onSeatHoldReleased(new SeatHoldReleasedEvent(SEAT_IDS, LOCK_TOKEN)))
                .doesNotThrowAnyException();
    }
}
