package com.tikit.queue.service;

import com.tikit.common.ErrorCode;
import com.tikit.queue.config.QueueProperties;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.redis.QueueRedisRepository;
import com.tikit.queue.redis.QueueRedisRepository.Admission;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class AdmissionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final UUID ADMISSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ENTRY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final QueueRedisRepository queueRedisRepository = mock(QueueRedisRepository.class);

    @Test
    @DisplayName("본인과 같은 회차의 유효한 입장 권한이면 통과한다")
    void validate_success() {
        given(queueRedisRepository.findAdmission(ADMISSION_ID))
                .willReturn(Optional.of(new Admission(ADMISSION_ID, MEMBER_ID, SESSION_ID, ENTRY_ID)));

        assertThatCode(() -> service(true).validate(ADMISSION_ID, MEMBER_ID, SESSION_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("만료됐거나 다 쓴 입장 권한이면 ADMISSION_INVALID")
    void validate_missing() {
        given(queueRedisRepository.findAdmission(ADMISSION_ID)).willReturn(Optional.empty());

        assertRejected(() -> service(true).validate(ADMISSION_ID, MEMBER_ID, SESSION_ID), ErrorCode.ADMISSION_INVALID);
    }

    @Test
    @DisplayName("다른 회원의 입장 권한이면 ADMISSION_INVALID")
    void validate_otherMember() {
        given(queueRedisRepository.findAdmission(ADMISSION_ID))
                .willReturn(Optional.of(new Admission(ADMISSION_ID, 2L, SESSION_ID, ENTRY_ID)));

        assertRejected(() -> service(true).validate(ADMISSION_ID, MEMBER_ID, SESSION_ID), ErrorCode.ADMISSION_INVALID);
    }

    @Test
    @DisplayName("다른 회차의 입장 권한이면 ADMISSION_INVALID")
    void validate_otherSession() {
        given(queueRedisRepository.findAdmission(ADMISSION_ID))
                .willReturn(Optional.of(new Admission(ADMISSION_ID, MEMBER_ID, 99L, ENTRY_ID)));

        assertRejected(() -> service(true).validate(ADMISSION_ID, MEMBER_ID, SESSION_ID), ErrorCode.ADMISSION_INVALID);
    }

    @Test
    @DisplayName("Redis 장애로 입장 권한을 확인할 수 없으면 503")
    void validate_redisDown() {
        given(queueRedisRepository.findAdmission(ADMISSION_ID))
                .willThrow(new RedisConnectionFailureException("redis down"));

        assertRejected(() -> service(true).validate(ADMISSION_ID, MEMBER_ID, SESSION_ID), ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("검사를 끄면 Redis를 보지 않는다")
    void validate_notRequired() {
        AdmissionService service = service(false);

        service.validate(ADMISSION_ID, MEMBER_ID, SESSION_ID);
        service.consume(ADMISSION_ID);

        assertThat(service.isRequired()).isFalse();
        then(queueRedisRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입장 권한 소비가 Redis 장애로 실패해도 예외를 던지지 않는다")
    void consume_redisDown() {
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(queueRedisRepository).consumeAdmission(ADMISSION_ID);

        assertThatCode(() -> service(true).consume(ADMISSION_ID)).doesNotThrowAnyException();
    }

    private AdmissionService service(boolean required) {
        return new AdmissionService(queueRedisRepository, new QueueProperties(
                100, Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(2), 20,
                Duration.ofHours(6), required));
    }

    private void assertRejected(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(QueueException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
