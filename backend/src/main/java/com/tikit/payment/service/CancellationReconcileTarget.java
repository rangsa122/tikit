package com.tikit.payment.service;

import com.tikit.payment.entity.CancellationStatus;

// 복구 작업이 확인할 취소 정보 (엔티티를 트랜잭션 밖으로 넘기지 않으려고 값만 담음)
public record CancellationReconcileTarget(
        Long cancellationId,
        Long bookingId,
        Long memberId,
        CancellationStatus status,
        String paymentKey,
        Long refundAmount
) {

    // 환불 결과를 아직 모르는 취소 (처리 중: 서버가 Toss 취소 중 멈춤 / 결과 확인 중: 응답 유실)
    public boolean isInProgress() {
        return status == CancellationStatus.PROCESSING || status == CancellationStatus.RECONCILING;
    }
}