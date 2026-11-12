package com.tikit.rag.chunking;

/** 임베딩 생성 전의 청크. chunkOrder는 rag_chunks.chunk_order에 대응하며 0부터 시작한다. */
public record RagTextChunk(int chunkOrder, String content) {
}
