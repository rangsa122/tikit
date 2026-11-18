package com.tikit.payment.service;

import com.tikit.payment.entity.PaymentAttemptStatus;

// 복구 작업이 확인할 결제 정보 (엔티티를 트랜잭션 밖으로 넘기지 않으려고 값만 담음)
public record PaymentReconcileTarget(
        Long paymentAttemptId,
        Long memberId,
        PaymentAttemptStatus status,
        String paymentKey,
        String orderId,
        Long amount
) {

    // 승인 결과를 아직 모르는 결제 (처리 중: 서버가 Toss 호출 중 멈춤 / 결과 확인 중: 응답 유실)
    public boolean isInProgress() {
        return status == PaymentAttemptStatus.PROCESSING || status == PaymentAttemptStatus.RECONCILING;
    }
}