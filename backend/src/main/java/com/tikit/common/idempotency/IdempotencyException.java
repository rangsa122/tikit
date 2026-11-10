package com.tikit.common.idempotency;

import com.tikit.common.ErrorCode;
import lombok.Getter;

// 멱등키 충돌 (같은 키 다른 내용, 같은 키 처리 중)
@Getter
public class IdempotencyException extends RuntimeException {

    private final ErrorCode errorCode;

    public IdempotencyException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}