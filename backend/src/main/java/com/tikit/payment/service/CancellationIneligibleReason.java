package com.tikit.payment.service;

// 전체 취소 불가 사유 (견적 응답의 reasonCode, 취소 가능하면 null)
public enum CancellationIneligibleReason {
    BOOKING_NOT_CONFIRMED,          // 확정 예매가 아님 (결제 대기·만료)
    ALREADY_CANCELED,               // 이미 취소된 예매
    CANCELLATION_IN_PROGRESS,       // 진행 중인 취소가 있음
    CANCELLATION_DEADLINE_PASSED    // 취소 기한이 지남
}