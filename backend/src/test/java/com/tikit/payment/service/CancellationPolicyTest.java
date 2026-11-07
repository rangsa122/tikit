package com.tikit.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class CancellationPolicyTest {

    private static final Instant DEADLINE = Instant.parse("2026-12-01T09:00:00Z");

    @Test
    @DisplayName("저장된 정책 버전 v1으로 v1 정책을 찾는다")
    void from_v1_returnsV1() {
        // given
        String version = "v1";

        // when
        CancellationPolicy policy = CancellationPolicy.from(version);
        System.out.println("테스트: " + policy);

        // then
        assertThat(policy).isEqualTo(CancellationPolicy.V1);
    }

    @Test
    @DisplayName("알 수 없는 정책 버전이면 데이터 오류로 예외가 발생한다")
    void from_unknownVersion_throws() {
        // given
        String version = "cancel-v9";

        // when
        Throwable thrown = catchThrowable(() -> CancellationPolicy.from(version));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("V1: 취소 기한 1초 전에는 취소할 수 있다")
    void isCancellable_beforeDeadline_true() {
        // given
        Instant now = DEADLINE.minusSeconds(1);

        // when
        boolean cancellable = CancellationPolicy.V1.isCancellable(now, DEADLINE);
        System.out.println("테스트: " + cancellable);

        // then
        assertThat(cancellable).isTrue();
    }

    @Test
    @DisplayName("V1: 취소 기한 정각에는 취소할 수 없다")
    void isCancellable_atDeadline_false() {
        // given
        Instant now = DEADLINE;

        // when
        boolean cancellable = CancellationPolicy.V1.isCancellable(now, DEADLINE);
        System.out.println("테스트: " + cancellable);

        // then
        assertThat(cancellable).isFalse();
    }

    @Test
    @DisplayName("V1: 수수료는 결제액과 관계없이 0원이다")
    void calculateFee_v1_zero() {
        // given
        Long paidAmount = 50000L;

        // when
        Long fee = CancellationPolicy.V1.calculateFee(paidAmount);
        System.out.println("테스트: fee=" + fee);

        // then
        assertThat(fee).isZero();
    }
}
