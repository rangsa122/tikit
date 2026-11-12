package com.tikit.rag.controller.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.rag.exception.RagException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.tikit.rag.controller")
public class RagControllerAdvice {

    // Service 또는 Controller에서 던진 RAG 예외 처리
    @ExceptionHandler(RagException.class)
    public ResponseEntity<ResponseDTO<Void>> handleRagException(
            RagException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode));
    }

    // @Valid 검증 실패: 빈 제목, 빈 본문, 제목 길이 초과 등
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDTO<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> java.util.Objects.requireNonNullElse(
                        error.getDefaultMessage(), "입력값을 확인해 주세요."))
                .findFirst()
                .orElse("입력값을 확인해 주세요.");

        return ResponseEntity.badRequest()
                .body(ResponseDTO.fail(ErrorCode.VALIDATION_ERROR, message));
    }

    // 숫자 자리에 문자를 넣은 요청 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseDTO<Void>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        String message =
                exception.getName() + "의 입력 형식이 올바르지 않습니다.";

        return ResponseEntity.badRequest()
                .body(ResponseDTO.fail(ErrorCode.VALIDATION_ERROR, message));
    }
}
