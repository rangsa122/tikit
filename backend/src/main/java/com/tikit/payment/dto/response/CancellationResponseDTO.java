package com.tikit.payment.dto.response;

import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// 예매 전체 취소 결과 (SUCCEEDED는 Toss 취소와 예매 취소 반영 완료, 카드사 입금 완료가 아님)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationResponseDTO {
    private Long cancellationId;
    private Long bookingId;
    private CancellationStatus status;
    private Long refundAmount;
    private Long feeAmount;
    private String reason;
    private CancellationInitiatorRole initiatorRole;
    private boolean retryAllowed;
    private String failureCode;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime serverTime;
}