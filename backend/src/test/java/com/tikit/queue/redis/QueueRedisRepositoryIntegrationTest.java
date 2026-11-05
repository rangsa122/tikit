package com.tikit.queue.redis;

import com.tikit.queue.entity.QueueEntryStatus;
import com.tikit.queue.redis.QueueRedisRepository.Admission;
import com.tikit.queue.redis.QueueRedisRepository.QueueLimits;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis로 대기열 Lua 스크립트를 검증한다.
 * 회차 ID를 아주 큰 임의 값으로 잡아 다른 데이터와 겹치지 않게 하고, 끝나면 키를 지운다.
 * QUEUE_IT=true ./gradlew test --tests '*QueueRedisRepositoryIntegrationTest'
 */
@ActiveProfiles("local")
@SpringBootTest(properties = "booking.expiry.scheduler.enabled=false")
@EnabledIfEnvironmentVariable(named = "QUEUE_IT", matches = "true")
class QueueRedisRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    private static final QueueLimits LIMITS = new QueueLimits(
            2, Duration.ofMinutes(10), Duration.ofSeconds(30), 20, Duration.ofMinutes(10));

    @Autowired
    private QueueRedisRepository repository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Long sessionId;
    private final List<QueueEntrySnapshot> created = new ArrayList<>();

    @BeforeEach
    void setup() {
        sessionId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
    }

    @AfterEach
    void cleanup() {
        List<String> keys = new ArrayList<>(List.of(
                key("seq"), key("waiting"), key("admitted"), key("members")));
        for (QueueEntrySnapshot entry : created) {
            keys.add("queue-entry:" + entry.queueEntryId());
            if (entry.admissionId() != null) {
                keys.add("admission:" + entry.admissionId());
            }
        }
        redisTemplate.delete(keys);
    }

    @Test
    @DisplayName("정원까지는 바로 입장하고 그 뒤부터는 1번부터 순번을 받는다")
    void join_admitsUpToCapacity() {
        QueueEntrySnapshot first = join(1L, NOW);
        QueueEntrySnapshot second = join(2L, NOW);
        QueueEntrySnapshot third = join(3L, NOW);
        QueueEntrySnapshot fourth = join(4L, NOW);

        assertThat(first.status()).isEqualTo(QueueEntryStatus.ADMITTED);
        assertThat(first.position()).isZero();
        assertThat(first.admissionId()).isNotNull();
        assertThat(first.admissionExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(second.status()).isEqualTo(QueueEntryStatus.ADMITTED);
        assertThat(third.status()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(third.position()).isEqualTo(1);
        assertThat(third.admissionId()).isNull();
        assertThat(fourth.position()).isEqualTo(2);
        assertThat(fourth.waitingCount()).isEqualTo(2);
        assertThat(first.waitingCount()).isZero();
        assertThat(first.created()).isTrue();

        Optional<Admission> admission = repository.findAdmission(first.admissionId());
        assertThat(admission).contains(new Admission(first.admissionId(), 1L, sessionId, first.queueEntryId()));
    }

    @Test
    @DisplayName("같은 회원이 다시 줄을 서면 기존 항목을 그대로 돌려준다")
    void join_sameMemberReturnsExisting() {
        join(1L, NOW);
        join(2L, NOW);
        QueueEntrySnapshot waiting = join(3L, NOW);

        QueueEntrySnapshot again = join(3L, NOW.plusSeconds(1));

        assertThat(again.queueEntryId()).isEqualTo(waiting.queueEntryId());
        assertThat(again.created()).isFalse();
        assertThat(again.position()).isEqualTo(1);
    }

    @Test
    @DisplayName("입장 권한을 소비하면 정원이 비어 다음 대기자가 조회할 때 입장한다")
    void consume_admitsNext() {
        QueueEntrySnapshot first = join(1L, NOW);
        join(2L, NOW);
        QueueEntrySnapshot third = join(3L, NOW);

        repository.consumeAdmission(first.admissionId());
        QueueEntrySnapshot firstAfter = find(first, 1L, NOW.plusSeconds(1)).orElseThrow();
        QueueEntrySnapshot thirdAfter = find(third, 3L, NOW.plusSeconds(1)).orElseThrow();

        assertThat(firstAfter.status()).isEqualTo(QueueEntryStatus.CONSUMED);
        assertThat(firstAfter.position()).isNull();
        assertThat(repository.findAdmission(first.admissionId())).isEmpty();
        assertThat(thirdAfter.status()).isEqualTo(QueueEntryStatus.ADMITTED);
        assertThat(thirdAfter.admissionId()).isNotNull();
        created.add(thirdAfter);
    }

    @Test
    @DisplayName("입장한 뒤 다 쓴 회원이 다시 줄을 서면 새 항목을 받는다")
    void join_afterConsumedCreatesNewEntry() {
        QueueEntrySnapshot first = join(1L, NOW);
        repository.consumeAdmission(first.admissionId());

        QueueEntrySnapshot again = join(1L, NOW.plusSeconds(1));

        assertThat(again.queueEntryId()).isNotEqualTo(first.queueEntryId());
        assertThat(again.created()).isTrue();
        assertThat(again.status()).isEqualTo(QueueEntryStatus.ADMITTED);
    }

    @Test
    @DisplayName("입장 시간이 지나면 EXPIRED가 되고 다음 대기자가 입장한다")
    void admission_expires() {
        QueueEntrySnapshot first = join(1L, NOW);
        join(2L, NOW);
        QueueEntrySnapshot third = join(3L, NOW);
        Instant later = NOW.plus(Duration.ofMinutes(10)).plusSeconds(1);

        // 대기자는 계속 조회하고 있었다고 침
        find(third, 3L, later.minusSeconds(5));
        QueueEntrySnapshot thirdAfter = find(third, 3L, later).orElseThrow();
        QueueEntrySnapshot firstAfter = find(first, 1L, later).orElseThrow();

        assertThat(firstAfter.status()).isEqualTo(QueueEntryStatus.EXPIRED);
        assertThat(thirdAfter.status()).isEqualTo(QueueEntryStatus.ADMITTED);
        created.add(thirdAfter);
    }

    @Test
    @DisplayName("조회가 끊긴 대기자는 입장 차례에 건너뛰고 EXPIRED")
    void staleWaiting_isSkipped() {
        QueueEntrySnapshot first = join(1L, NOW);
        join(2L, NOW);
        QueueEntrySnapshot stale = join(3L, NOW);
        QueueEntrySnapshot active = join(4L, NOW.plusSeconds(40));

        repository.consumeAdmission(first.admissionId());
        QueueEntrySnapshot activeAfter = find(active, 4L, NOW.plusSeconds(41)).orElseThrow();
        QueueEntrySnapshot staleAfter = find(stale, 3L, NOW.plusSeconds(42)).orElseThrow();

        assertThat(activeAfter.status()).isEqualTo(QueueEntryStatus.ADMITTED);
        assertThat(staleAfter.status()).isEqualTo(QueueEntryStatus.EXPIRED);
        created.add(activeAfter);
    }

    @Test
    @DisplayName("대기 취소와 입장 종료는 한 번만 반영되고 다시 요청해도 본인 항목이면 true")
    void leave_waitingAndAdmitted() {
        QueueEntrySnapshot first = join(1L, NOW);
        join(2L, NOW);
        QueueEntrySnapshot third = join(3L, NOW);

        assertThat(repository.leave(third.queueEntryId(), 3L)).isTrue();
        assertThat(repository.leave(third.queueEntryId(), 3L)).isTrue();
        assertThat(repository.leave(first.queueEntryId(), 1L)).isTrue();

        assertThat(find(third, 3L, NOW).orElseThrow().status()).isEqualTo(QueueEntryStatus.LEFT);
        assertThat(find(first, 1L, NOW).orElseThrow().status()).isEqualTo(QueueEntryStatus.LEFT);
        assertThat(repository.findAdmission(first.admissionId())).isEmpty();

        // 입장 종료로 빈 정원에 새로 온 사람이 바로 입장
        assertThat(join(4L, NOW).status()).isEqualTo(QueueEntryStatus.ADMITTED);
    }

    @Test
    @DisplayName("다른 회원의 항목은 조회하거나 취소할 수 없다")
    void otherMember_cannotAccess() {
        QueueEntrySnapshot first = join(1L, NOW);

        assertThat(repository.find(first.queueEntryId(), 2L, NOW, LIMITS)).isEmpty();
        assertThat(repository.leave(first.queueEntryId(), 2L)).isFalse();
    }

    @Test
    @DisplayName("동시에 많이 줄을 서도 정원보다 많이 입장하지 않고 순번이 겹치지 않는다")
    void concurrentJoin_respectsCapacity() throws Exception {
        int members = 30;
        List<Callable<QueueEntrySnapshot>> tasks = new ArrayList<>();
        for (long memberId = 1; memberId <= members; memberId++) {
            long id = memberId;
            tasks.add(() -> repository.join(sessionId, id, NOW, LIMITS));
        }

        List<QueueEntrySnapshot> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (Future<QueueEntrySnapshot> future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }
        }
        created.addAll(results);

        long admitted = results.stream().filter(r -> r.status() == QueueEntryStatus.ADMITTED).count();
        assertThat(admitted).isEqualTo(2);

        // 마지막 순번 기준으로 다시 조회하면 1부터 빈틈없이 이어짐
        List<Integer> positions = results.stream()
                .filter(r -> r.status() == QueueEntryStatus.WAITING)
                .map(r -> find(r, r.memberId(), NOW).orElseThrow().position())
                .sorted()
                .toList();
        assertThat(positions).hasSize(members - 2);
        for (int i = 0; i < positions.size(); i++) {
            assertThat(positions.get(i)).isEqualTo(i + 1);
        }
    }

    private QueueEntrySnapshot join(Long memberId, Instant now) {
        QueueEntrySnapshot entry = repository.join(sessionId, memberId, now, LIMITS);
        created.add(entry);
        return entry;
    }

    private Optional<QueueEntrySnapshot> find(QueueEntrySnapshot entry, Long memberId, Instant now) {
        return repository.find(entry.queueEntryId(), memberId, now, LIMITS);
    }

    private String key(String name) {
        return "queue:" + sessionId + ":" + name;
    }
}
