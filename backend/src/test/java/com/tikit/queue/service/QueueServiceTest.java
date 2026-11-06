package com.tikit.queue.service;

import com.tikit.common.ErrorCode;
import com.tikit.queue.config.QueueProperties;
import com.tikit.queue.dto.response.QueueEntryResponseDTO;
import com.tikit.queue.entity.QueueEntryStatus;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.redis.QueueEntrySnapshot;
import com.tikit.queue.redis.QueueRedisRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class QueueServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    private static final UUID ENTRY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMISSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final QueueProperties PROPERTIES = new QueueProperties(
            100, Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(2), 20, Duration.ofHours(6), true);

    private QueueRedisRepository queueRedisRepository;
    private QueueSaleValidator queueSaleValidator;
    private QueueService queueService;

    @BeforeEach
    void setup() {
        queueRedisRepository = mock(QueueRedisRepository.class);
        queueSaleValidator = mock(QueueSaleValidator.class);
        queueService = new QueueService(queueRedisRepository, queueSaleValidator, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("판매 중인 회차에 줄을 서면 대기 순번과 2초 뒤 재조회를 돌려준다")
    void join_waiting() {
        // given
        given(queueRedisRepository.join(eq(SESSION_ID), eq(MEMBER_ID), eq(NOW), any()))
                .willReturn(waiting(12, true));

        // when
        QueueResult result = queueService.join(MEMBER_ID, SESSION_ID);

        // then
        assertThat(result.created()).isTrue();
        QueueEntryResponseDTO entry = result.entry();
        assertThat(entry.queueEntryId()).isEqualTo(ENTRY_ID.toString());
        assertThat(entry.status()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(entry.position()).isEqualTo(12);
        assertThat(entry.waitingCount()).isEqualTo(40);
        assertThat(entry.admissionId()).isNull();
        assertThat(entry.admissionExpiresAt()).isNull();
        assertThat(entry.pollAfterSeconds()).isEqualTo(2);
        assertThat(entry.serverTime().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        then(queueSaleValidator).should().validateOnSale(SESSION_ID, NOW);
    }

    @Test
    @DisplayName("판매 중이 아니면 줄을 세우지 않는다")
    void join_saleNotOpen() {
        // given
        willThrow(new QueueException(ErrorCode.SALE_NOT_OPEN))
                .given(queueSaleValidator).validateOnSale(SESSION_ID, NOW);

        // when, then
        assertRejected(() -> queueService.join(MEMBER_ID, SESSION_ID), ErrorCode.SALE_NOT_OPEN);
        then(queueRedisRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 입장한 항목이면 200용 결과와 입장 권한을 돌려준다")
    void join_existingAdmitted() {
        // given
        given(queueRedisRepository.join(eq(SESSION_ID), eq(MEMBER_ID), eq(NOW), any()))
                .willReturn(admitted(false));

        // when
        QueueResult result = queueService.join(MEMBER_ID, SESSION_ID);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.entry().position()).isZero();
        assertThat(result.entry().admissionId()).isEqualTo(ADMISSION_ID.toString());
        assertThat(result.entry().admissionExpiresAt().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(result.entry().pollAfterSeconds()).isEqualTo(5);
    }

    @Test
    @DisplayName("Redis 장애면 503")
    void join_redisDown() {
        // given
        given(queueRedisRepository.join(any(), any(), any(), any()))
                .willThrow(new RedisConnectionFailureException("redis down"));

        // when, then
        assertRejected(() -> queueService.join(MEMBER_ID, SESSION_ID), ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("본인 항목이 없으면 404")
    void get_notFound() {
        // given
        given(queueRedisRepository.find(eq(ENTRY_ID), eq(MEMBER_ID), eq(NOW), any())).willReturn(Optional.empty());

        // when, then
        assertRejected(() -> queueService.get(MEMBER_ID, ENTRY_ID.toString()), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("대기 항목 ID 형식이 틀리면 Redis를 거치지 않고 404")
    void get_invalidId() {
        assertRejected(() -> queueService.get(MEMBER_ID, "not-a-uuid"), ErrorCode.RESOURCE_NOT_FOUND);
        then(queueRedisRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("끝난 항목은 순번과 입장 권한 없이 돌려준다")
    void get_finished() {
        // given
        QueueEntrySnapshot consumed = new QueueEntrySnapshot(ENTRY_ID, SESSION_ID, MEMBER_ID,
                QueueEntryStatus.CONSUMED, null, 0, ADMISSION_ID, NOW, false);
        given(queueRedisRepository.find(eq(ENTRY_ID), eq(MEMBER_ID), eq(NOW), any())).willReturn(Optional.of(consumed));

        // when
        QueueEntryResponseDTO entry = queueService.get(MEMBER_ID, ENTRY_ID.toString());

        // then
        assertThat(entry.status()).isEqualTo(QueueEntryStatus.CONSUMED);
        assertThat(entry.position()).isNull();
        assertThat(entry.admissionId()).isNull();
        assertThat(entry.admissionExpiresAt()).isNull();
    }

    @Test
    @DisplayName("본인 항목이 없으면 대기 취소도 404")
    void leave_notFound() {
        // given
        given(queueRedisRepository.leave(ENTRY_ID, MEMBER_ID)).willReturn(false);

        // when, then
        assertRejected(() -> queueService.leave(MEMBER_ID, ENTRY_ID.toString()), ErrorCode.RESOURCE_NOT_FOUND);
    }

    private QueueEntrySnapshot waiting(int position, boolean created) {
        return new QueueEntrySnapshot(ENTRY_ID, SESSION_ID, MEMBER_ID, QueueEntryStatus.WAITING,
                position, 40, null, null, created);
    }

    private QueueEntrySnapshot admitted(boolean created) {
        return new QueueEntrySnapshot(ENTRY_ID, SESSION_ID, MEMBER_ID, QueueEntryStatus.ADMITTED,
                0, 40, ADMISSION_ID, NOW.plus(Duration.ofMinutes(5)), created);
    }

    private void assertRejected(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(QueueException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
