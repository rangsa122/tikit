package com.tikit.payment.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationCreateRequestDTOTest {

    private Validator validator;

    @BeforeEach
    void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("사유·예상 환불 금액·정책 버전이 올바르면 검증을 통과한다")
    void valid_request() {
        // given
        CancellationCreateRequestDTO request = new CancellationCreateRequestDTO("개인 사정으로 취소합니다.", 50000L, "v1");

        // when
        Set<ConstraintViolation<CancellationCreateRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("취소 사유가 공백이면 검증에 실패한다")
    void blank_reason() {
        // given
        CancellationCreateRequestDTO request = new CancellationCreateRequestDTO("  ", 50000L, "v1");

        // when
        Set<ConstraintViolation<CancellationCreateRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("취소 사유는 필수 입력 항목입니다.");
    }

    @Test
    @DisplayName("취소 사유가 200자를 넘으면 검증에 실패한다")
    void too_long_reason() {
        // given
        CancellationCreateRequestDTO request = new CancellationCreateRequestDTO("가".repeat(201), 50000L, "v1");

        // when
        Set<ConstraintViolation<CancellationCreateRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("취소 사유는 200자 이내로 입력해야 합니다.");
    }

    @Test
    @DisplayName("예상 환불 금액이 음수이면 검증에 실패한다")
    void negative_expected_refund_amount() {
        // given
        CancellationCreateRequestDTO request = new CancellationCreateRequestDTO("개인 사정", -1L, "v1");

        // when
        Set<ConstraintViolation<CancellationCreateRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("예상 환불 금액은 0원 이상이어야 합니다.");
    }

    @Test
    @DisplayName("정책 버전이 없으면 검증에 실패한다")
    void missing_policy_version() {
        // given
        CancellationCreateRequestDTO request = new CancellationCreateRequestDTO("개인 사정", 50000L, null);

        // when
        Set<ConstraintViolation<CancellationCreateRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("취소 정책 버전은 필수 입력 항목입니다.");
    }
}