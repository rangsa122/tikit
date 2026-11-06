package com.tikit.booking.controller.advice;

import com.tikit.booking.controller.BookingController;
import com.tikit.booking.exception.BookingException;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = BookingController.class)
public class BookingControllerAdvice {

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ResponseDTO<Void>> handleBookingException(BookingException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("booking rejected: {}", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }
}
