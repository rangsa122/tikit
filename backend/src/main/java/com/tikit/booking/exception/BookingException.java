package com.tikit.booking.exception;

import com.tikit.common.ErrorCode;
import lombok.Getter;

@Getter
public class BookingException extends RuntimeException {

    private final ErrorCode errorCode;

    public BookingException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
