package com.tikit.payment.dto.response;

import com.tikit.common.job.BackgroundJobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// 재확인 요청 접수 결과 (명세 JobAccepted, 확정 여부는 결제·취소 조회로 확인)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationJobResponseDTO {
    private Long jobId;
    private BackgroundJobStatus status;
    private Long resourceId;
    private OffsetDateTime serverTime;
}