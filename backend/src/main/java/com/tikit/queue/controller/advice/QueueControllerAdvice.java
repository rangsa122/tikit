package com.tikit.queue.controller.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.queue.controller.QueueController;
import com.tikit.queue.exception.QueueException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(assignableTypes = QueueController.class)
public class QueueControllerAdvice {

    @ExceptionHandler(QueueException.class)
    public ResponseEntity<ResponseDTO<Void>> handleQueueException(QueueException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("queue rejected: {}", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }

    // sessionId가 숫자가 아니면 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseDTO<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }
}
