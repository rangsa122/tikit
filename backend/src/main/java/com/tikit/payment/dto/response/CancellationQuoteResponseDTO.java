package com.tikit.payment.dto.response;

import com.tikit.payment.service.CancellationIneligibleReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// 전체 취소 예상 금액 (조회만, 실제 취소 요청 때 다시 계산)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationQuoteResponseDTO {
    private Long bookingId;
    private boolean eligible;
    private CancellationIneligibleReason reasonCode;
    private Long paidAmount;
    private Long feeAmount;
    private Long refundAmount;
    private String policyVersion;
    private OffsetDateTime cancellationDeadline;
    private OffsetDateTime quotedAt;
    private OffsetDateTime serverTime;
}
