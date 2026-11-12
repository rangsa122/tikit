package com.tikit.common.job;

public enum BackgroundJobStatus {
    QUEUED,     // 실행 대기 (next_run_at이 되면 실행)
    RUNNING,    // 실행 중 (점유 기한 안에 한 서버만 처리)
    SUCCEEDED,  // 완료 (대상이 확정됨)
    FAILED      // 포기 (자동으로 더 시도하지 않음, 관리자 확인 대상)
}
