package com.tikit.payment.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Arrays;

// 취소 정책 버전별 규칙 (예매 생성 때 저장된 policyVersion으로 찾음)
@Getter
@RequiredArgsConstructor
public enum CancellationPolicy {

    // 취소 기한 전이면 전액 환불(수수료 0원), 기한 후 취소 불가
    V1("v1");

    private final String version;

    // 예매 생성 때 저장된 정책 버전으로 찾음 (모르는 버전이면 데이터 오류)
    public static CancellationPolicy from(String version) {
        return Arrays.stream(values())
                .filter(policy -> policy.version.equals(version))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("알 수 없는 취소 정책 버전입니다. version=" + version));
    }

    // 현재 시각이 취소 기한 전이여야 취소 가능 (기한과 같거나 지나면 불가)
    public boolean isCancellable(Instant now, Instant deadline) {
        return now.isBefore(deadline);
    }

    // 결제액 기준 수수료 (v1은 0원)
    public Long calculateFee(Long paidAmount) {
        return 0L;
    }
}
