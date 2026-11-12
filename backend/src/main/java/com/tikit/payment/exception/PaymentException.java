package com.tikit.payment.exception;

import com.tikit.common.ErrorCode;
import lombok.Getter;

// 결제 업무 규칙 위반 (예매 상태, 결제 기한, 진행 중 결제 등)
@Getter
public class PaymentException extends RuntimeException {

    private final ErrorCode errorCode;

    public PaymentException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}