package com.tikit.payment.dto.toss;

// Toss 결제 승인 요청 본문
public record TossConfirmRequestDTO(
        String paymentKey,
        String orderId,
        Long amount
) {
}