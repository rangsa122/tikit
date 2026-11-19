package com.tikit.payment.service;

import com.tikit.common.job.BackgroundJobStatus;

import java.time.Instant;

// 관리자 재확인 요청 접수 결과 (작업 엔티티를 트랜잭션 밖으로 넘기지 않으려고 값만 담음)
public record ReconciliationAccepted(
        Long jobId,
        BackgroundJobStatus status,
        Long resourceId,
        Instant serverTime
) {
}