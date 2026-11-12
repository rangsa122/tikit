package com.tikit.rag.search;

public record RagPrototypeChunk(String chunkId, String documentId, String documentTitle,
                                int chunkOrder, String content) {
}
