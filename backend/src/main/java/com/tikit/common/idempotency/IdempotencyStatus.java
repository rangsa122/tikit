package com.tikit.common.idempotency;

// 멱등 요청 처리 상태 (DB 제약: PROCESSING, SUCCEEDED, FAILED)
public enum IdempotencyStatus {
    PROCESSING,     // 처리 중 (같은 키 재요청은 409)
    SUCCEEDED,      // 처리 완료 (같은 키 재요청은 저장된 결과 재사용)
    FAILED          // 처리 실패 (같은 키로 다시 처리 허용)
}
