package com.tikit.payment.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class CancellationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    private Cancellation cancellation;

    @BeforeEach
    void setup() {
        cancellation = Cancellation.create(10L, 1L, CancellationInitiatorRole.MEMBER, "개인 사정",
                50000L, 0L, "v1", "cancel-test-key");
    }

    @Test
    @DisplayName("생성하면 접수 상태이고 환불액은 결제액에서 수수료를 뺀 값이다")
    void create_requested() {
        // given
        // setup에서 이미 셋팅

        // when
        CancellationStatus status = cancellation.getStatus();
        System.out.println("테스트: status=" + status + ", refund=" + cancellation.getRefundAmount());

        // then
        assertThat(status).isEqualTo(CancellationStatus.REQUESTED);
        assertThat(cancellation.getRefundAmount()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("처리를 시작하면 처리 중이 되고, 두 번 시작하면 예외가 발생한다")
    void startProcessing_once() {
        // given
        // setup에서 이미 셋팅

        // when
        cancellation.startProcessing();
        Throwable thrown = catchThrowable(() -> cancellation.startProcessing());
        System.out.println("테스트: " + cancellation.getStatus() + ", " + thrown);

        // then
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.PROCESSING);
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("처리 중에 환불이 성공하면 거래 키와 완료 시각을 저장한다")
    void succeed_fromProcessing() {
        // given
        // setup에서 이미 셋팅
        cancellation.startProcessing();

        // when
        cancellation.succeed("cancel-transaction-key", NOW);
        System.out.println("테스트: " + cancellation.getStatus() + ", " + cancellation.getPgTransactionKey());

        // then
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
        assertThat(cancellation.getPgTransactionKey()).isEqualTo("cancel-transaction-key");
        assertThat(cancellation.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("결과 확인 중이던 취소도 복구 작업이 성공으로 확정할 수 있다")
    void succeed_fromReconciling() {
        // given
        // setup에서 이미 셋팅
        cancellation.startProcessing();
        cancellation.markReconciling();

        // when
        cancellation.succeed("cancel-transaction-key", NOW);
        System.out.println("테스트: " + cancellation.getStatus());

        // then
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("거래 키 없이 성공 처리하면 예외가 발생한다")
    void succeed_withoutTransactionKey_throws() {
        // given
        // setup에서 이미 셋팅
        cancellation.startProcessing();

        // when
        Throwable thrown = catchThrowable(() -> cancellation.succeed(" ", NOW));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.PROCESSING);
    }

    @Test
    @DisplayName("처리 시작 전(접수)에는 성공·실패로 바꿀 수 없다")
    void succeedOrFail_beforeProcessing_throws() {
        // given
        // setup에서 이미 셋팅

        // when
        Throwable succeedThrown = catchThrowable(() -> cancellation.succeed("cancel-transaction-key", NOW));
        Throwable failThrown = catchThrowable(() -> cancellation.fail("REJECT"));
        System.out.println("테스트: " + succeedThrown + " / " + failThrown);

        // then
        assertThat(succeedThrown).isInstanceOf(IllegalStateException.class);
        assertThat(failThrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("처리 중에 Toss가 거절하면 실패 코드를 저장한다")
    void fail_fromProcessing() {
        // given
        // setup에서 이미 셋팅
        cancellation.startProcessing();

        // when
        cancellation.fail("NOT_CANCELABLE_PAYMENT");
        System.out.println("테스트: " + cancellation.getStatus() + ", " + cancellation.getFailureCode());

        // then
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.FAILED);
        assertThat(cancellation.getFailureCode()).isEqualTo("NOT_CANCELABLE_PAYMENT");
    }

    @Test
    @DisplayName("결과 확인 중으로는 처리 중에서만 바꿀 수 있다")
    void markReconciling_onlyFromProcessing() {
        // given
        // setup에서 이미 셋팅

        // when
        Throwable thrown = catchThrowable(() -> cancellation.markReconciling());
        cancellation.startProcessing();
        cancellation.markReconciling();
        System.out.println("테스트: " + thrown + ", 이후 status=" + cancellation.getStatus());

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.RECONCILING);
    }
}