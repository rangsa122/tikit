package com.tikit.common.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.common.idempotency.IdempotencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

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
}