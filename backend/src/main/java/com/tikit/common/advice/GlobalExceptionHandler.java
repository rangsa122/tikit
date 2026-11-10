package com.tikit.common.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.common.idempotency.IdempotencyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseDTO<Void>> handleInvalidJson(
            HttpMessageNotReadableException e
    ) {

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(
                        errorCode,
                        "요청 본문이 없거나 JSON 형식이 올바르지 않습니다."));
    }

    // @Valid 검증 실패 시 첫 번째 필드 오류 메시지로 VALIDATION_ERROR 응답
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDTO<Void>> handleValidation(
            MethodArgumentNotValidException e
    ) {

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode, message));
    }

    // 멱등키 충돌 (같은 키 다른 내용, 같은 키 처리 중) → 409 IDEMPOTENCY_CONFLICT
    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ResponseDTO<Void>> handleIdempotencyException(IdempotencyException e) {
        log.warn("idempotency conflict: code={}, {}", e.getErrorCode().name(), e.getMessage());

        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode));
    }

    // 필수 헤더 누락 (예: Idempotency-Key) → 400 VALIDATION_ERROR
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ResponseDTO<Void>> handleMissingHeader(MissingRequestHeaderException e) {

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode, e.getHeaderName() + " 헤더가 필요합니다."));
    }
}
