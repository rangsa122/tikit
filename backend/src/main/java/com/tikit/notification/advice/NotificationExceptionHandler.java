package com.tikit.notification.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.notification.controller.NotificationController;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;


// 예외 처리 - 사이트 내부 알림 API
@RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionHandler {

    // 존재하지 않거나 본인 소유가 아닌 알림
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ResponseDTO<Void>> handleNotFound(
            NoSuchElementException e
    ) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode, e.getMessage()));
    }

    // 페이지 번호 또는 크기 오류
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseDTO<Void>> handleValidation(
            IllegalArgumentException e
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode, e.getMessage()));
    }

    // 인증 정보 없음
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ResponseDTO<Void>> handleUnauthorized(
            AuthenticationCredentialsNotFoundException e
    ) {
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode, e.getMessage()));
    }
}
