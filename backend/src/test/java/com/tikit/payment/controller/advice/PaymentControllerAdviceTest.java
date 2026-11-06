package com.tikit.payment.controller.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentControllerAdviceTest {

    private PaymentControllerAdvice paymentControllerAdvice;

    @BeforeEach
    void setup() {
        paymentControllerAdvice = new PaymentControllerAdvice();
    }

    @Test
    @DisplayName("Toss가 결제를 거절하면 PAYMENT_DECLINED로 응답한다")
    void handle_toss_api_exception() {
        // given
        // setup에서 이미 셋팅
        TossApiException e = new TossApiException(
                404,
                "NOT_FOUND_PAYMENT",
                "존재하지 않는 결제 정보 입니다."
        );

        // when
        ResponseEntity<ResponseDTO<Void>> response =
                paymentControllerAdvice.handleTossApiException(e);

        // then
        assertFailResponse(response, ErrorCode.PAYMENT_DECLINED);
    }

    @Test
    @DisplayName("Toss 서버 장애,키 문제,연결 실패는 SERVICE_UNAVAILABLE로 응답한다")
    void handle_toss_unavailable_exception() {
        // given
        // setup에서 이미 셋팅
        TossUnavailableException e = new TossUnavailableException("Toss 연결 실패");

        // when
        ResponseEntity<ResponseDTO<Void>> response =
                paymentControllerAdvice.handleTossUnavailableException(e);

        // then
        assertFailResponse(response, ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Toss 응답을 받지 못하면 PAYMENT_IN_PROGRESS로 응답한다")
    void handle_toss_result_unknown_exception() {
        // given
        // setup에서 이미 셋팅
        TossResultUnknownException e = new TossResultUnknownException("Toss 응답 시간 초과");

        // when
        ResponseEntity<ResponseDTO<Void>> response =
                paymentControllerAdvice.handleTossResultUnknownException(e);

        // then
        assertFailResponse(response, ErrorCode.PAYMENT_IN_PROGRESS);
    }

    @Test
    @DisplayName("결제 업무 규칙 위반은 예외에 담긴 ErrorCode로 응답한다")
    void handle_payment_exception() {
        // given
        // setup에서 이미 셋팅
        PaymentException e = new PaymentException(
                ErrorCode.BOOKING_EXPIRED,
                "bookingId=1, memberId=1"
        );

        // when
        ResponseEntity<ResponseDTO<Void>> response =
                paymentControllerAdvice.handlePaymentException(e);

        // then
        assertFailResponse(response,  ErrorCode.BOOKING_EXPIRED);
    }

    private void assertFailResponse(
            ResponseEntity<ResponseDTO<Void>> response,
            ErrorCode errorCode
    ) {
        String responseBody = JsonMapper.builder().build().writeValueAsString(response.getBody());
        int httpStatusCode = response.getStatusCode().value();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        assertThat(response.getStatusCode()).isEqualTo(errorCode.getStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(errorCode.name());
        assertThat(response.getBody().getMessage()).isEqualTo(errorCode.getMessage());
        assertThat(response.getBody().getData()).isNull();
    }
}
