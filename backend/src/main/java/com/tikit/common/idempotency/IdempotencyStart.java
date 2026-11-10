package com.tikit.common.idempotency;

// 멱등 처리 시작 결과 (replay면 이미 처리된 요청 → 저장된 결과 리소스로 응답)
public record IdempotencyStart(Long requestId, boolean replay, String resourceType, Long resourceId) {

    public static IdempotencyStart started(Long requestId) {
        return new IdempotencyStart(requestId, false, null, null);
    }

    public static IdempotencyStart replay(Long requestId, String resourceType, Long resourceId) {
        return new IdempotencyStart(requestId, true, resourceType, resourceId);
    }
}