package com.tikit.payment.exception;

// 결제 시도가 없거나 본인 결제가 아닌 경우
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
