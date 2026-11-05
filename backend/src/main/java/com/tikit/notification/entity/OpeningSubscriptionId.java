package com.tikit.notification.entity;

import java.io.Serializable;
import java.util.Objects;


// 공연별 알림 신청 복합 기본키
public class OpeningSubscriptionId implements Serializable {

    private Long memberId;
    private Long performanceId;

    public OpeningSubscriptionId() {
    }

    public OpeningSubscriptionId(Long memberId, Long performanceId) {
        this.memberId = memberId;
        this.performanceId = performanceId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getPerformanceId() {
        return performanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpeningSubscriptionId that)) return false;

        return Objects.equals(memberId, that.memberId)
                && Objects.equals(performanceId, that.performanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, performanceId);
    }
}
