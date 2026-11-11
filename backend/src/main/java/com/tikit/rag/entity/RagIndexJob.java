package com.tikit.rag.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "rag_index_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RagIndexJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RagIndexJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static RagIndexJob createQueued(
            Long documentId,
            Integer documentVersion,
            Long adminId
    ) {
        Objects.requireNonNull(documentId, "문서 ID는 필수입니다.");
        Objects.requireNonNull(documentVersion, "문서 버전은 필수입니다.");
        Objects.requireNonNull(adminId, "요청자 ID는 필수입니다.");

        if (documentVersion < 1) {
            throw new IllegalArgumentException(
                    "문서 버전은 1 이상이어야 합니다."
            );
        }

        RagIndexJob job = new RagIndexJob();
        job.documentId = documentId;
        job.documentVersion = documentVersion;
        job.status = RagIndexJobStatus.QUEUED;
        job.attemptCount = 0;
        job.requestedBy = adminId;

        return job;
    }
}