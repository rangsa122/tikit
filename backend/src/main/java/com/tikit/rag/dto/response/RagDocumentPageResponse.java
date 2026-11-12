package com.tikit.rag.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record RagDocumentPageResponse(
        List<RagDocumentSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static RagDocumentPageResponse from(
            Page<RagDocumentSummaryResponse> result
    ) {
        return new RagDocumentPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}