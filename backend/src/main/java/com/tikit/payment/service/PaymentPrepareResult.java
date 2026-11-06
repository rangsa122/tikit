package com.tikit.payment.service;

import com.tikit.payment.dto.response.PaymentAttemptResponseDTO;

// 결제 준비 결과 (새 결제 시도를 만들었으면 201, 기존 READY를 돌려주면 200)
public record PaymentPrepareResult(
        PaymentAttemptResponseDTO paymentAttempt,
        boolean created
) {

}
