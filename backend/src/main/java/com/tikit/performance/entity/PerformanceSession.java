package com.tikit.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "performance_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
//회차
public class PerformanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Column(name = "opening_id", nullable = false)
    private Long openingId;

    @Column(name = "seat_template_id", nullable = false)
    private Long seatTemplateId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "sales_close_at", nullable = false)
    private Instant salesCloseAt;

    @Column(name = "cancellation_deadline", nullable = false)
    private Instant cancellationDeadline;

    @Column(name = "policy_version", nullable = false, length = 30)
    private String policyVersion;

    @Column(name = "cancellation_policy", nullable = false, columnDefinition = "TEXT")
    private String cancellationPolicy;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PerformanceSession create(Performance performance, Long openingId,
                                            Long seatTemplateId, Instant startsAt, Instant salesCloseAt,
                                            Instant cancellationDeadline, String policyVersion, String cancellationPolicy) {
        PerformanceSession s = new PerformanceSession();
        s.performance = performance;
        s.openingId = openingId;
        s.seatTemplateId = seatTemplateId;
        s.startsAt = startsAt;
        s.salesCloseAt = salesCloseAt;
        s.cancellationDeadline = cancellationDeadline;
        s.policyVersion = policyVersion;
        s.cancellationPolicy = cancellationPolicy;
        return s;
    }
}