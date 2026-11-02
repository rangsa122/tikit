package com.tikit.performance.controller.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.exception.PerformanceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.tikit.performance")
@Slf4j
public class PerformanceControllerAdvice {

    @ExceptionHandler(PerformanceException.class)
    public ResponseEntity<ResponseDTO<Void>> handlePerformanceException(PerformanceException e) {
        log.warn("performance error: {}", e.getMessage());
        return toResponse(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDTO<Void>> handleValidation(MethodArgumentNotValidException e) {
        return toResponse(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ResponseDTO<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.warn("optimistic lock conflict: {}", e.getMessage());
        return toResponse(ErrorCode.VERSION_CONFLICT);
    }

    private ResponseEntity<ResponseDTO<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode));
    }
}