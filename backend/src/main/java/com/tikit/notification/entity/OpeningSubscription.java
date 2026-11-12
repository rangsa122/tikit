package com.tikit.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;


// 공연별 예매 시작 알림 신청 정보
@Entity
@Table(name = "opening_subscriptions")
@IdClass(OpeningSubscriptionId.class)
public class OpeningSubscription {

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Id
    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    // 알림 신청 일시
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected OpeningSubscription() {
    }

    public OpeningSubscription(Long memberId, Long performanceId) {
        this.memberId = memberId;
        this.performanceId = performanceId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getPerformanceId() {
        return performanceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
