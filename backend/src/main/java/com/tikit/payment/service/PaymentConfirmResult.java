package com.tikit.payment.service;

import com.tikit.payment.dto.response.PaymentAttemptResponseDTO;

// 결제 승인 결과 (승인 완료면 200, 처리 중 또는 결과 확인 중이면 202)
public record PaymentConfirmResult(
        PaymentAttemptResponseDTO paymentAttempt,
        boolean approved
) {

}
