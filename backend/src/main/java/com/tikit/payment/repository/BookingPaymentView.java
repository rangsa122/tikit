package com.tikit.payment.repository;

import java.time.Instant;

// 결제 준비에 필요한 예매·회원 정보 (Booking 엔티티가 없어서 인터페이스 프로젝션 사용)
public interface BookingPaymentView {

    Long getBookingId();

    String getStatus();

    Instant getExpiresAt();

    Long getTotalAmount();

    String getMemberStatus();

    String getMemberRole();
}