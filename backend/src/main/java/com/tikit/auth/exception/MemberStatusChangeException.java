package com.tikit.auth.exception;

import com.tikit.common.ErrorCode;
import lombok.Getter;

@Getter
public class MemberStatusChangeException extends RuntimeException {

    private final ErrorCode errorCode;

    public MemberStatusChangeException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
