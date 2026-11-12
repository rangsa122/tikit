package com.tikit.payment.dto.response;

import com.tikit.booking.entity.BookingStatus;
import com.tikit.payment.entity.PaymentAttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttemptResponseDTO {
    private Long paymentAttemptId;
    private Long bookingId;
    private String orderId;
    private String orderName;
    private String customerKey;
    private Long amount;
    private String currency;
    private PaymentAttemptStatus status;
    private BookingStatus bookingStatus;
    private OffsetDateTime expiresAt;
    private boolean retryAllowed;
    private String failureCode;
    private OffsetDateTime serverTime;
}