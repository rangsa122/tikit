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
@Table(name = "rag_document_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RagDocumentVersion {

    @EmbeddedId
    private RagDocumentVersionId id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", nullable = false, length = 20)
    private RagIndexStatus indexStatus;

    @Column(name = "embedding_model", length = 120)
    private String embeddingModel;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void startIndexing(String model, int dimensions) {
        if (indexStatus != RagIndexStatus.QUEUED && indexStatus != RagIndexStatus.NOT_INDEXED
                && indexStatus != RagIndexStatus.FAILED) {
            throw new IllegalStateException("인덱싱을 시작할 수 없는 문서 버전 상태입니다.");
        }
        embeddingModel = Objects.requireNonNull(model, "임베딩 모델은 필수입니다.");
        if (dimensions < 1) {
            throw new IllegalArgumentException("임베딩 차원은 1 이상이어야 합니다.");
        }
        embeddingDimensions = dimensions;
        indexStatus = RagIndexStatus.RUNNING;
    }

    public void completeIndexing() {
        if (indexStatus != RagIndexStatus.RUNNING) {
            throw new IllegalStateException("실행 중인 문서 버전만 완료할 수 있습니다.");
        }
        indexStatus = RagIndexStatus.READY;
    }

    public void failIndexing() {
        if (indexStatus != RagIndexStatus.RUNNING) {
            throw new IllegalStateException("실행 중인 문서 버전만 실패 처리할 수 있습니다.");
        }
        indexStatus = RagIndexStatus.FAILED;
    }

    public static RagDocumentVersion createFirstVersion(
            Long documentId,
            String title,
            String content,
            String contentHash,
            Long adminId
    ) {
        RagDocumentVersion documentVersion = new RagDocumentVersion();

        documentVersion.id = new RagDocumentVersionId(
                Objects.requireNonNull(documentId, "문서 ID는 필수입니다."),
                1
        );
        documentVersion.title =
                Objects.requireNonNull(title, "제목은 필수입니다.");
        documentVersion.content =
                Objects.requireNonNull(content, "본문은 필수입니다.");
        documentVersion.contentHash =
                Objects.requireNonNull(contentHash, "본문 해시는 필수입니다.");
        documentVersion.indexStatus = RagIndexStatus.QUEUED;
        documentVersion.createdBy =
                Objects.requireNonNull(adminId, "작성자 ID는 필수입니다.");

        return documentVersion;
    }
}
