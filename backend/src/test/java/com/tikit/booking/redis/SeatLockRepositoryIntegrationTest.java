package com.tikit.booking.redis;

import com.tikit.booking.redis.SeatLockRepository.HoldResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis로 좌석 락 스크립트를 검증한다.
 * BOOKING_IT=true ./gradlew test --tests '*SeatLockRepositoryIntegrationTest'
 */
@ActiveProfiles("local")
@SpringBootTest(properties = "booking.expiry.scheduler.enabled=false")
@EnabledIfEnvironmentVariable(named = "BOOKING_IT", matches = "true")
class SeatLockRepositoryIntegrationTest {

    private static final Duration TTL = Duration.ofMinutes(1);

    @Autowired
    private SeatLockRepository seatLockRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final List<String> keys = new ArrayList<>();
    private String me;
    private String other;
    private long seat;

    @BeforeEach
    void setup() {
        me = UUID.randomUUID().toString();
        other = UUID.randomUUID().toString();
        // 실제 좌석 ID와 겹치지 않게 큰 값 사용
        seat = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        for (int i = 0; i < 6; i++) {
            keys.add("seat-hold:" + (seat + i));
        }
        keys.add("seat-selection:" + me);
        keys.add("seat-selection:" + other);
        keys.add("admission:" + me);
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete(keys);
    }

    @Test
    @DisplayName("먼저 잡은 사람만 성공하고, 같은 사람이 다시 눌러도 성공한다")
    void hold_firstWins() {
        assertThat(seatLockRepository.hold(seat, me, 4, false, TTL)).isEqualTo(HoldResult.HELD);
        assertThat(seatLockRepository.hold(seat, other, 4, false, TTL)).isEqualTo(HoldResult.TAKEN);
        assertThat(seatLockRepository.hold(seat, me, 4, false, TTL)).isEqualTo(HoldResult.HELD);
        assertThat(selectionSize(me)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 좌석을 동시에 눌러도 한 명만 잡는다")
    void hold_concurrent() throws Exception {
        int users = 30;
        ExecutorService executor = Executors.newFixedThreadPool(users);
        List<Callable<HoldResult>> calls = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            String token = UUID.randomUUID().toString();
            keys.add("seat-selection:" + token);
            calls.add(() -> seatLockRepository.hold(seat, token, 4, false, TTL));
        }

        List<HoldResult> results = new ArrayList<>();
        for (Future<HoldResult> future : executor.invokeAll(calls)) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(results).filteredOn(result -> result == HoldResult.HELD).hasSize(1);
    }

    @Test
    @DisplayName("매수를 넘으면 잡지 않고, 하나를 풀면 다시 잡을 수 있다")
    void hold_limit() {
        assertThat(seatLockRepository.hold(seat, me, 2, false, TTL)).isEqualTo(HoldResult.HELD);
        assertThat(seatLockRepository.hold(seat + 1, me, 2, false, TTL)).isEqualTo(HoldResult.HELD);
        assertThat(seatLockRepository.hold(seat + 2, me, 2, false, TTL)).isEqualTo(HoldResult.LIMIT_EXCEEDED);

        seatLockRepository.unlockAll(List.of(seat), me);

        assertThat(seatLockRepository.hold(seat + 2, me, 2, false, TTL)).isEqualTo(HoldResult.HELD);
    }

    @Test
    @DisplayName("입장 권한이 남은 시간만큼만 잡는다")
    void hold_followsAdmissionTtl() {
        redisTemplate.opsForValue().set("admission:" + me, "1:1:x", Duration.ofSeconds(20));

        seatLockRepository.hold(seat, me, 4, true, Duration.ofMinutes(5));

        Long ttl = redisTemplate.getExpire("seat-hold:" + seat, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 20L);
    }

    @Test
    @DisplayName("운영 모드에서 입장 권한이 사라졌으면 좌석을 잡지 않는다")
    void hold_rejectsWithoutAdmission() {
        assertThat(seatLockRepository.hold(seat, me, 4, true, TTL)).isEqualTo(HoldResult.ADMISSION_EXPIRED);

        assertThat(redisTemplate.hasKey("seat-hold:" + seat)).isFalse();
        assertThat(selectionSize(me)).isZero();
    }

    @Test
    @DisplayName("예매 생성은 내가 누른 좌석을 이어받고 시간을 늘린다")
    void lockAll_takesOverOwnSeats() {
        seatLockRepository.hold(seat, me, 4, false, Duration.ofSeconds(20));

        boolean locked = seatLockRepository.lockAll(List.of(seat, seat + 1), me, Duration.ofMinutes(5));

        assertThat(locked).isTrue();
        assertThat(redisTemplate.getExpire("seat-hold:" + seat, TimeUnit.SECONDS)).isGreaterThan(60L);
    }

    @Test
    @DisplayName("다른 사람이 잡은 좌석이 섞이면 아무것도 잡지 않는다")
    void lockAll_rejectsOthersSeat() {
        seatLockRepository.hold(seat, other, 4, false, TTL);

        boolean locked = seatLockRepository.lockAll(List.of(seat + 1, seat), me, TTL);

        assertThat(locked).isFalse();
        assertThat(redisTemplate.hasKey("seat-hold:" + (seat + 1))).isFalse();
    }

    @Test
    @DisplayName("다른 사람의 좌석은 풀 수 없다")
    void unlockAll_onlyOwn() {
        seatLockRepository.hold(seat, other, 4, false, TTL);

        seatLockRepository.unlockAll(List.of(seat), me);

        assertThat(seatLockRepository.findHolders(List.of(seat)).get(seat)).isEqualTo(other);
    }

    @Test
    @DisplayName("예매에 넣지 않은 좌석만 풀고 선택 목록을 지운다")
    void clearSelection_keepsBookedSeats() {
        seatLockRepository.hold(seat, me, 4, false, TTL);
        seatLockRepository.hold(seat + 1, me, 4, false, TTL);

        seatLockRepository.clearSelection(me, List.of(seat));

        assertThat(seatLockRepository.findHolders(List.of(seat, seat + 1))).containsOnlyKeys(seat);
        assertThat(selectionSize(me)).isZero();
    }

    private Long selectionSize(String token) {
        return redisTemplate.opsForSet().size("seat-selection:" + token);
    }
}
