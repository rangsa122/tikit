package com.tikit.rag.dto.response;

import com.tikit.rag.entity.RagDocument;
import com.tikit.rag.entity.RagDocumentType;
import com.tikit.rag.entity.RagDocumentVersion;
import com.tikit.rag.entity.RagIndexStatus;

import java.time.Instant;

public record RagDocumentSummaryResponse(
        Long id,
        RagDocumentType type,
        String title,
        Integer latestVersion,
        Integer activeVersion,
        RagIndexStatus indexStatus,
        Instant createdAt,
        Instant updatedAt
) {

    public static RagDocumentSummaryResponse from(
            RagDocument document,
            RagDocumentVersion latestVersion
    ) {
        return new RagDocumentSummaryResponse(
                document.getId(),
                document.getType(),
                latestVersion.getTitle(),
                document.getLatestVersion(),
                document.getActiveVersion(),
                latestVersion.getIndexStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}