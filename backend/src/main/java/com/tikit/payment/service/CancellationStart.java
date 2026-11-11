package com.tikit.payment.service;

// 취소 1차 트랜잭션 결과: Toss 취소 요청에 필요한 값 (모두 DB에서 꺼낸 값)
public record CancellationStart(
        Long cancellationId,
        Long bookingId,
        String paymentKey,
        String reason,
        Long refundAmount,
        String pgIdempotencyKey
) {
}