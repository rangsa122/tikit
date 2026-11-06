package com.tikit.payment.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class PaymentAttemptTest {

    private static final String PAYMENT_KEY = "tgen_20261030abcdef";

    private PaymentAttempt attempt;
    private Instant now;

    @BeforeEach
    void setup() {
        // 결제 준비 직후 상태 (READY)
        attempt = PaymentAttempt.create(1L, "TIKIT_0123456789abcdef0123456789abcdef", 50000L, "pg-idempotency-key-1");
        now = Instant.parse("2026-10-30T06:00:00Z");
    }

    @Test
    @DisplayName("새 결제 시도는 READY 상태로 시작한다")
    void create_starts_as_ready() {
        // given
        // setup에 이미 셋팅

        // when
        PaymentAttemptStatus status = attempt.getStatus();
        System.out.println("테스트: " + status);

        // then
        assertThat(status).isEqualTo(PaymentAttemptStatus.READY);
        assertThat(attempt.isReady()).isTrue();
        assertThat(attempt.isInProgress()).isFalse();
        assertThat(attempt.isApproved()).isFalse();
    }

    @Test
    @DisplayName("승인 시작하면 PROCESSING이 되고 결제 키와 요청 시각이 저장된다")
    void start_confirmation_changes_ready_to_processing() {
        // given
        // setup에서 이미 셋팅

        // when
        attempt.startConfirmation(PAYMENT_KEY, now);
        System.out.println("테스트: " + attempt.getStatus());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(attempt.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(attempt.getConfirmationRequestedAt()).isEqualTo(now);
        assertThat(attempt.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("READY가 아니면 승인을 다시 시작할 수 없다")
    void start_confirmation_fails_when_not_ready() {
        // given
        attempt.startConfirmation(PAYMENT_KEY, now);

        // when
        Throwable thrown = catchThrowable(() -> attempt.startConfirmation("other-key", now));
        System.out.println("테스트: " + thrown.getMessage());

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(attempt.getPaymentKey()).isEqualTo(PAYMENT_KEY);
    }

    @Test
    @DisplayName("PROCESSING에서 승인 완료하면 APPROVED가 되고 승인 시각이 저장된다")
    void approve_from_processing() {
        // given
        attempt.startConfirmation(PAYMENT_KEY, now);
        Instant approvedAt = now.plusSeconds(2);

        // when
        attempt.approve(approvedAt);
        System.out.println("테스트: " + attempt.getStatus());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
        assertThat(attempt.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(attempt.isApproved()).isTrue();
        assertThat(attempt.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("RECONCILING에서도 승인 완료할 수 있다 (복구 작업)")
    void approve_from_reconciling() {
        // given
        attempt.startConfirmation(PAYMENT_KEY, now);
        attempt.markReconciling();

        // when
        attempt.approve(now.plusSeconds(60));
        System.out.println("테스트: " + attempt.getStatus());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
    }

    @Test
    @DisplayName("READY에서는 승인 완료할 수 없다")
    void approve_fails_when_ready() {
        // given
        // setup에서 이미 셋팅

        // when
        Throwable thrown = catchThrowable(() -> attempt.approve(now));
        System.out.println("테스트: " + thrown.getMessage());

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.READY);
    }

    @Test
    @DisplayName("PROCESSING에서 실패 확정하면 FAILED가 되고 실패 코드가 저장된다")
    void fail_from_processing() {
        // given
        attempt.startConfirmation(PAYMENT_KEY, now);

        // when
        attempt.fail("REJECT_CARD_COMPANY");
        System.out.println("테스트: " + attempt.getStatus() + ", " + attempt.getFailureCode());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempt.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(attempt.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("이미 승인된 결제는 실패로 바꿀 수 없다")
    void fail_fails_when_approved() {
        // given
        attempt.startConfirmation(PAYMENT_KEY, now);
        attempt.approve(now.plusSeconds(2));

        // when
        Throwable thrown = catchThrowable(() -> attempt.fail("SOME_ERROR"));
        System.out.println("테스트: " + thrown.getMessage());

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
        assertThat(attempt.getFailureCode()).isNull();
    }

    @Test
    @DisplayName("PROCESSING에서 결과 확인 중으로 바꿀 수 있다 (응답 유실)")
    void mark_reconciling_from_processing() {
        // given
        attempt.startConfirmation(PAYMENT_KEY, now);

        // when
        attempt.markReconciling();
        System.out.println("테스트: " + attempt.getStatus());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.RECONCILING);
        assertThat(attempt.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("READY에서는 결과 확인 중으로 바꿀 수 없다")
    void mark_reconciling_fails_when_ready() {
        // given
        // setup에서 이미 셋팅

        // when
        Throwable thrown = catchThrowable(() -> attempt.markReconciling());
        System.out.println("테스트: " + thrown.getMessage());

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.READY);
    }
}
