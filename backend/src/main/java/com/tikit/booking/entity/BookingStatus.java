package com.tikit.booking.entity;

public enum BookingStatus {
    PENDING_PAYMENT,    // 좌석 선점 후 결제 대기 (임시 예매)
    CONFIRMED,          // 결제 승인으로 예매 확정
    EXPIRED,            // 결제 기한 초과로 만료
    CANCELED            // 결제 전 취소 또는 확정 후 전체 취소
}
