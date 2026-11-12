package com.tikit.common.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    private IdempotencyRequest request;

    @BeforeEach
    void setup() {
        request = IdempotencyRequest.start(1L, "PAYMENT_CONFIRM", "key-1", "hash-a", NOW, Duration.ofHours(24));
    }

    @Test
    @DisplayName("처음 요청은 처리 중으로 저장되고 24시간 뒤 만료 시각을 가진다")
    void start_processingWithExpiry() {
        // given
        // setup에서 이미 셋팅

        // when
        IdempotencyStatus status = request.getStatus();
        System.out.println("테스트: status=" + status + ", expiresAt=" + request.getExpiresAt());

        // then
        assertThat(status).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(request.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(request.getResourceId()).isNull();
    }

    @Test
    @DisplayName("요청 해시가 같으면 같은 요청, 다르면 다른 요청이다")
    void isSameRequest_comparesHash() {
        // given
        // setup에서 이미 셋팅

        // when
        boolean same = request.isSameRequest("hash-a");
        boolean different = request.isSameRequest("hash-b");
        System.out.println("테스트: same=" + same + ", different=" + different);

        // then
        assertThat(same).isTrue();
        assertThat(different).isFalse();
    }

    @Test
    @DisplayName("성공하면 결과 리소스를 기록하고, 실패 후 다시 시작하면 처리 중으로 돌아간다")
    void succeed_fail_restart() {
        // given
        // setup에서 이미 셋팅

        // when
        request.succeed("PAYMENT_ATTEMPT", 10L);
        System.out.println("테스트: " + request.getStatus() + ", " + request.getResourceType() + "=" + request.getResourceId());

        IdempotencyRequest failed = IdempotencyRequest.start(1L, "PAYMENT_CONFIRM", "key-2", "hash-a", NOW, Duration.ofHours(24));
        failed.fail();
        failed.restart();

        // then
        assertThat(request.getStatus()).isEqualTo(IdempotencyStatus.SUCCEEDED);
        assertThat(request.getResourceType()).isEqualTo("PAYMENT_ATTEMPT");
        assertThat(request.getResourceId()).isEqualTo(10L);
        assertThat(failed.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
    }
}