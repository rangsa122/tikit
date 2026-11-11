package com.tikit.rag.exception;

import com.tikit.common.ErrorCode;
import lombok.Getter;

@Getter
public class RagException extends RuntimeException {

    private final ErrorCode errorCode;

    public RagException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}