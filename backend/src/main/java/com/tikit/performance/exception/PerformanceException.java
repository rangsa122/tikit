package com.tikit.performance.exception;

import com.tikit.common.ErrorCode;
import lombok.Getter;

@Getter
public class PerformanceException extends  RuntimeException{

    private final ErrorCode errorCode;

    public PerformanceException(ErrorCode errorCode){

        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
