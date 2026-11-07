package com.tikit.common.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyHasherTest {

    private static final String BODY = "{\"orderId\":\"TIKIT_1\",\"amount\":50000}";

    @Test
    @DisplayName("대상과 본문이 같으면 항상 같은 64자 해시가 나온다")
    void hash_sameInput_sameHash() {
        // given
        String target = "PAYMENT_ATTEMPT:10";

        // when
        String first = IdempotencyHasher.hash(target, BODY);
        String second = IdempotencyHasher.hash(target, BODY);
        System.out.println("테스트: " + first);

        // then
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    @DisplayName("본문이 같아도 대상 리소스가 다르면 다른 해시가 나온다")
    void hash_differentTarget_differentHash() {
        // given
        String target10 = "PAYMENT_ATTEMPT:10";
        String target11 = "PAYMENT_ATTEMPT:11";

        // when
        String hash10 = IdempotencyHasher.hash(target10, BODY);
        String hash11 = IdempotencyHasher.hash(target11, BODY);
        System.out.println("테스트: " + hash10 + " / " + hash11);

        // then
        assertThat(hash10).isNotEqualTo(hash11);
    }

    @Test
    @DisplayName("본문이 없는 요청도 대상만으로 해시를 만든다")
    void hash_noBody_usesTarget() {
        // given
        String target = "BOOKING:262";

        // when
        String hash = IdempotencyHasher.hash(target, null);
        System.out.println("테스트: " + hash);

        // then
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(IdempotencyHasher.hash(target, ""));
    }
}