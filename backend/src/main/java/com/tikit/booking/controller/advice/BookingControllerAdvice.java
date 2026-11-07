package com.tikit.booking.controller.advice;

import com.tikit.booking.controller.BookingController;
import com.tikit.booking.controller.MyBookingController;
import com.tikit.booking.controller.SeatHoldController;
import com.tikit.booking.exception.BookingException;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(assignableTypes = {BookingController.class, MyBookingController.class, SeatHoldController.class})
public class BookingControllerAdvice {

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ResponseDTO<Void>> handleBookingException(BookingException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("booking rejected: {}", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }

    // 숫자가 아닌 bookingId, 잘못된 status, 범위를 벗어난 page와 size
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ResponseDTO<Void>> handleInvalidParameter(Exception e) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }
}
