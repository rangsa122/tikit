package com.tikit.payment.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfirmRequestDTOTest {

    private Validator validator;
    private PaymentConfirmRequestDTO request;

    @BeforeEach
    void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        // 모든 필드가 올바른 요청 (각 테스트에서 한 필드만 바꿔서 검증)
        request = new PaymentConfirmRequestDTO(
                "tgen_20261030abcdef",
                "TIKIT_0123456789abcdef0123456789abcdef",
                50000L
        );
    }

    @Test
    @DisplayName("올바른 승인 요청은 검증 오류가 없다")
    void confirm_valid_request_has_no_violation() {
        // given
        // setup에서 이미 셋팅

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("결제 금액 0원과 1억 원은 경계값이라 통과한다")
    void confirm_amount_boundary_has_no_violation() {
        // given
        // setup에서 이미 셋팅
        PaymentConfirmRequestDTO minRequest = new PaymentConfirmRequestDTO(
                request.getPaymentKey(), request.getOrderId(), 0L);
        PaymentConfirmRequestDTO maxRequest = new PaymentConfirmRequestDTO(
                request.getPaymentKey(), request.getOrderId(), 100_000_000L);

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> minViolations = validator.validate(minRequest);
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> maxViolations = validator.validate(maxRequest);
        System.out.println("테스트: " + minViolations + ", " + maxViolations);

        // then
        assertThat(minViolations).isEmpty();
        assertThat(maxViolations).isEmpty();
    }

    @Test
    @DisplayName("결제 키가 비어 있으면 필수 입력 오류가 난다")
    void confirm_blank_payment_key() {
        // given
        // setup에서 이미 셋팅
        request.setPaymentKey("");

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "paymentKey", "결제 키는 필수 입력 항목입니다.");
    }

    @Test
    @DisplayName("결제 키가 200자를 넘으면 길이 오류가 난다")
    void confirm_too_long_payment_key() {
        // given
        // setup에서 이미 셋팅
        request.setPaymentKey("a".repeat(201));

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "paymentKey", "결제 키는 200자 이내로 입력해야 합니다.");
    }

    @Test
    @DisplayName("주문 번호가 없으면 필수 입력 오류가 난다")
    void confirm_null_order_id() {
        // given
        // setup에서 이미 셋팅
        request.setOrderId(null);

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "orderId", "주문 번호는 필수 입력 항목입니다.");
    }

    @Test
    @DisplayName("주문 번호에 허용되지 않은 문자가 있으면 형식 오류가 난다")
    void confirm_invalid_order_id_format() {
        // given
        // setup에서 이미 셋팅
        request.setOrderId("TIKIT@123456");

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "orderId", "주문 번호 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("주문 번호가 6자보다 짧으면 형식 오류가 난다")
    void confirm_too_short_order_id() {
        // given
        // setup에서 이미 셋팅
        request.setOrderId("abc12");

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "orderId", "주문 번호 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("결제 금액이 없으면 필수 입력 오류가 난다")
    void confirm_null_amount() {
        // given
        // setup에서 이미 셋팅
        request.setAmount(null);

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "amount", "결제 금액은 필수 입력 항목입니다.");
    }

    @Test
    @DisplayName("결제 금액이 음수면 최솟값 오류가 난다")
    void confirm_negative_amount() {
        // given
        // setup에서 이미 셋팅
        request.setAmount(-1L);

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "amount", "결제 금액은 0원 이상이어야 합니다.");
    }

    @Test
    @DisplayName("결제 금액이 1억 원을 넘으면 최댓값 오류가 난다")
    void confirm_over_max_amount() {
        // given
        // setup에서 이미 셋팅
        request.setAmount(100_000_001L);

        // when
        Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations = validator.validate(request);
        System.out.println("테스트: " + violations);

        // then
        assertSingleViolation(violations, "amount", "결제 금액은 1억 원 이하여야 합니다.");
    }

    // 검증 오류가 정확히 1개이고, 기대한 필드와 메시지인지 확인
    private void assertSingleViolation(Set<ConstraintViolation<PaymentConfirmRequestDTO>> violations,
                                       String field, String message) {
        assertThat(violations).hasSize(1);

        ConstraintViolation<PaymentConfirmRequestDTO> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo(field);
        assertThat(violation.getMessage()).isEqualTo(message);
    }
}