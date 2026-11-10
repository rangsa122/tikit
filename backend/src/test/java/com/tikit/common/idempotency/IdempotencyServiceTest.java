package com.tikit.common.idempotency;

import com.tikit.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class IdempotencyServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String OPERATION_ID = "PAYMENT_CONFIRM";
    private static final String KEY = "key-1";
    private static final String HASH = "hash-a";
    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    private IdempotencyRequestRepository repository;
    private IdempotencyService service;

    @BeforeEach
    void setup() {
        repository = mock(IdempotencyRequestRepository.class);
        service = new IdempotencyService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        // 기본: 처음 온 키 (저장하면 ID 100 부여)
        given(repository.findByMemberIdAndOperationIdAndIdempotencyKey(MEMBER_ID, OPERATION_ID, KEY))
                .willReturn(Optional.empty());
        given(repository.saveAndFlush(any(IdempotencyRequest.class))).willAnswer(invocation -> {
            IdempotencyRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 100L);
            return request;
        });
    }

    // 이미 저장돼 있는 기록 (상태 지정)
    private IdempotencyRequest existing(IdempotencyStatus status) {
        IdempotencyRequest request = IdempotencyRequest.start(MEMBER_ID, OPERATION_ID, KEY, HASH,
                NOW.minusSeconds(60), Duration.ofHours(24));
        ReflectionTestUtils.setField(request, "id", 50L);
        switch (status) {
            case PROCESSING -> {
            }
            case SUCCEEDED -> request.succeed("PAYMENT_ATTEMPT", 10L);
            case FAILED -> request.fail();
        }
        given(repository.findByMemberIdAndOperationIdAndIdempotencyKey(MEMBER_ID, OPERATION_ID, KEY))
                .willReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("처음 온 키는 처리 중으로 저장하고 새로 처리하라고 알린다")
    void start_newKey_savesProcessing() {
        // given
        // setup에서 이미 셋팅

        // when
        IdempotencyStart result = service.start(MEMBER_ID, OPERATION_ID, KEY, HASH);
        System.out.println("테스트: " + result);

        // then
        assertThat(result.replay()).isFalse();
        assertThat(result.requestId()).isEqualTo(100L);
        then(repository).should().saveAndFlush(any(IdempotencyRequest.class));
    }

    @Test
    @DisplayName("같은 키·같은 내용의 완료된 요청은 다시 처리하지 않고 저장된 결과 리소스를 돌려준다")
    void start_succeededSameRequest_replays() {
        // given
        // setup에서 이미 셋팅
        existing(IdempotencyStatus.SUCCEEDED);

        // when
        IdempotencyStart result = service.start(MEMBER_ID, OPERATION_ID, KEY, HASH);
        System.out.println("테스트: " + result);

        // then
        assertThat(result.replay()).isTrue();
        assertThat(result.resourceType()).isEqualTo("PAYMENT_ATTEMPT");
        assertThat(result.resourceId()).isEqualTo(10L);
        then(repository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 키로 다른 내용을 보내면 완료된 요청이어도 409 IDEMPOTENCY_CONFLICT")
    void start_sameKeyDifferentRequest_conflict() {
        // given
        // setup에서 이미 셋팅
        existing(IdempotencyStatus.SUCCEEDED);

        // when
        Throwable thrown = catchThrowable(() -> service.start(MEMBER_ID, OPERATION_ID, KEY, "hash-b"));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IdempotencyException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    @DisplayName("같은 요청이 아직 처리 중이면 409로 막는다")
    void start_processing_conflict() {
        // given
        // setup에서 이미 셋팅
        existing(IdempotencyStatus.PROCESSING);

        // when
        Throwable thrown = catchThrowable(() -> service.start(MEMBER_ID, OPERATION_ID, KEY, HASH));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IdempotencyException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    @DisplayName("실패한 요청을 같은 키로 다시 보내면 처리 중으로 바꾸고 다시 처리하라고 알린다")
    void start_failed_restarts() {
        // given
        // setup에서 이미 셋팅
        IdempotencyRequest failed = existing(IdempotencyStatus.FAILED);

        // when
        IdempotencyStart result = service.start(MEMBER_ID, OPERATION_ID, KEY, HASH);
        System.out.println("테스트: " + result + ", status=" + failed.getStatus());

        // then
        assertThat(result.replay()).isFalse();
        assertThat(result.requestId()).isEqualTo(50L);
        assertThat(failed.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        then(repository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("처음 온 키가 동시에 들어와 저장이 유니크 제약에 걸리면 처리 중으로 보고 409")
    void start_concurrentInsert_conflict() {
        // given
        // setup에서 이미 셋팅
        given(repository.saveAndFlush(any(IdempotencyRequest.class)))
                .willThrow(new DataIntegrityViolationException("uq_idempotency_requests_1"));

        // when
        Throwable thrown = catchThrowable(() -> service.start(MEMBER_ID, OPERATION_ID, KEY, HASH));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IdempotencyException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    @DisplayName("처리 완료를 기록하면 SUCCEEDED와 결과 리소스가 저장된다")
    void succeed_recordsResource() {
        // given
        // setup에서 이미 셋팅
        IdempotencyRequest processing = existing(IdempotencyStatus.PROCESSING);
        given(repository.findById(50L)).willReturn(Optional.of(processing));

        // when
        service.succeed(50L, "PAYMENT_ATTEMPT", 10L);
        System.out.println("테스트: " + processing.getStatus() + ", " + processing.getResourceType() + "=" + processing.getResourceId());

        // then
        assertThat(processing.getStatus()).isEqualTo(IdempotencyStatus.SUCCEEDED);
        assertThat(processing.getResourceType()).isEqualTo("PAYMENT_ATTEMPT");
        assertThat(processing.getResourceId()).isEqualTo(10L);
    }
}