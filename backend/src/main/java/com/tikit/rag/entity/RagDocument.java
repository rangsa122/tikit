package com.tikit.rag.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "rag_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RagDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private RagDocumentType type;

    @Column(name = "performance_id")
    private Long performanceId;

    @Column(name = "latest_version", nullable = false)
    private Integer latestVersion;

    @Column(name = "active_version")
    private Integer activeVersion;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static RagDocument createPolicy(Long adminId) {
        RagDocument document = new RagDocument();

        document.type = RagDocumentType.POLICY;
        document.performanceId = null;
        document.latestVersion = 1;
        document.activeVersion = null;
        document.createdBy =
                Objects.requireNonNull(adminId, "작성자 ID는 필수입니다.");

        return document;
    }
}