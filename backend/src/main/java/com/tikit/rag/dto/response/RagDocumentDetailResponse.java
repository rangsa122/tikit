package com.tikit.rag.dto.response;

import com.tikit.rag.entity.RagDocument;
import com.tikit.rag.entity.RagDocumentType;
import com.tikit.rag.entity.RagDocumentVersion;
import com.tikit.rag.entity.RagIndexStatus;

import java.time.Instant;

public record RagDocumentDetailResponse(
        Long id,
        RagDocumentType type,
        String title,
        String content,
        Integer latestVersion,
        Integer activeVersion,
        RagIndexStatus indexStatus,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static RagDocumentDetailResponse from(
            RagDocument document,
            RagDocumentVersion latestVersion
    ) {
        return new RagDocumentDetailResponse(
                document.getId(),
                document.getType(),
                latestVersion.getTitle(),
                latestVersion.getContent(),
                document.getLatestVersion(),
                document.getActiveVersion(),
                latestVersion.getIndexStatus(),
                document.getCreatedBy(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}