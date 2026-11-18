package com.tikit.common.job;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

// 서버가 꺼져도 잊으면 안 되는 일 (결제·환불 결과 확인 등)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "background_jobs")
public class BackgroundJob {

    // 확인에 필요한 값은 대상 행(결제 시도, 취소)에 있으므로 payload는 비워 둠
    private static final String EMPTY_PAYLOAD = "{}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private BackgroundJobType type;

    @Column(name = "deduplication_key", nullable = false, unique = true, length = 180)
    private String deduplicationKey;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackgroundJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 실행 대기 작업 생성 (runAt이 되면 실행부가 꺼내 확인)
    public static BackgroundJob queue(BackgroundJobType type, Long resourceId, Instant runAt) {
        BackgroundJob job = new BackgroundJob();
        job.type = type;
        job.deduplicationKey = type.deduplicationKey(resourceId);
        job.resourceType = resourceTypeOf(type);
        job.resourceId = resourceId;
        job.payload = EMPTY_PAYLOAD;
        job.status = BackgroundJobStatus.QUEUED;
        job.attemptCount = 0;
        job.nextRunAt = runAt;
        return job;
    }

    // 작업 종류마다 확인할 대상 (멱등 기록의 결과 리소스 이름과 같게)
    private static String resourceTypeOf(BackgroundJobType type) {
        return switch (type) {
            case PAYMENT_RECONCILE -> "PAYMENT_ATTEMPT";
            case CANCELLATION_RECONCILE -> "CANCELLATION";
        };
    }
}