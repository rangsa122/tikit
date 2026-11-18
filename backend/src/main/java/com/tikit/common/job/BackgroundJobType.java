package com.tikit.common.job;

public enum BackgroundJobType {
    PAYMENT_RECONCILE,       // 결제 승인 결과 확인 (대상: 결제 시도)
    CANCELLATION_RECONCILE;  // 환불 결과 확인 (대상: 취소)

    // 같은 대상의 작업이 두 번 생기지 않게 하는 키
    public String deduplicationKey(Long resourceId) {
        return name() + ":" + resourceId;
    }
}
