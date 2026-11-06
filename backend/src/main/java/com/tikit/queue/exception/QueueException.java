package com.tikit.queue.exception;

import com.tikit.common.ErrorCode;
import lombok.Getter;

@Getter
public class QueueException extends RuntimeException {

    private final ErrorCode errorCode;

    public QueueException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
