package com.tikit.rag.dto.response;

public record RagSearchMatch(String chunkId, String documentId, String documentTitle,
                              int chunkOrder, String content, double score) {
}
