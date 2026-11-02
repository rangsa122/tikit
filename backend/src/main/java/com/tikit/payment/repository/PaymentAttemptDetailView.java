package com.tikit.payment.repository;

import java.time.Instant;

public interface PaymentAttemptDetailView {

    Long getPaymentAttemptId();

    Long getBookingId();

    String getOrderId();

    String getCustomerKey();

    Long getAmount();

    String getCurrency();

    String getStatus();

    String getFailureCode();

    String getBookingStatus();

    Instant getExpiresAt();

    String getPerformanceTitle();

    Integer getSeatCount();
}