package com.tikit.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;


// 사이트 내부 알림 엔티티
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림 수신 회원 ID
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "opening_id", nullable = false)
    private Long openingId;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private Instant createdAt;

    // 알림 생성
    public Notification(
            Long memberId,
            Long openingId,
            String type,
            String title,
            String message
    ) {
        this.memberId = memberId;
        this.openingId = openingId;
        this.type = type;
        this.title = title;
        this.message = message;
    }

    // 알림 읽음 처리 -- 반복 요청 시 기존 시각 유지
    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }
}
