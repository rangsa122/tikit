package com.tikit.payment.service;

// 승인 1차 트랜잭션 결과 (Toss를 부를지, 바로 응답할지 판단에 사용)
public record ConfirmationStart(
        ConfirmationStartStatus status,
        Long paymentAttemptId,
        String paymentKey,
        String orderId,
        Long amount,
        String pgIdempotencyKey
) {
}
