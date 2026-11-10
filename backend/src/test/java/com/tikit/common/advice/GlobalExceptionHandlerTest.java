package com.tikit.common.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.common.idempotency.IdempotencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MissingRequestHeaderException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setup() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("멱등키 충돌은 409와 IDEMPOTENCY_CONFLICT로 응답하고, 상세 내용은 응답에 넣지 않는다")
    void handleIdempotencyException_conflict409() {
        // given
        // setup에서 이미 셋팅
        IdempotencyException exception = new IdempotencyException(ErrorCode.IDEMPOTENCY_CONFLICT,
                "같은 키로 다른 요청입니다. memberId=1, operationId=PAYMENT_CONFIRM, idempotencyKey=key-1");

        // when
        ResponseEntity<ResponseDTO<Void>> response = handler.handleIdempotencyException(exception);
        System.out.println("테스트: " + response.getStatusCode().value() + ", "
                + response.getBody().getCode() + ", " + response.getBody().getMessage());

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT.getMessage());
        assertThat(response.getBody().getMessage()).doesNotContain("idempotencyKey");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("필수 헤더가 없으면 400 VALIDATION_ERROR와 헤더 이름을 알려 준다")
    void handleMissingHeader_validationError400() throws Exception {
        // given
        // setup에서 이미 셋팅
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("headerHolder", String.class), 0);
        MissingRequestHeaderException exception = new MissingRequestHeaderException("Idempotency-Key", parameter);

        // when
        ResponseEntity<ResponseDTO<Void>> response = handler.handleMissingHeader(exception);
        System.out.println("테스트: " + response.getStatusCode().value() + ", "
                + response.getBody().getCode() + ", " + response.getBody().getMessage());

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Idempotency-Key 헤더가 필요합니다.");
    }

    // MissingRequestHeaderException 생성용 (헤더를 받는 메서드 모양만 필요)
    private void headerHolder(String idempotencyKey) {
    }
}