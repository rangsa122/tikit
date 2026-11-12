package com.tikit.rag.worker;

/** 트랜잭션 종료 후에도 처리할 수 있도록 엔티티 대신 작업 스냅샷을 전달한다. */
public record RagIndexTask(Long jobId, Long documentId, int documentVersion,
                           String content, String embeddingModel, int embeddingDimensions, String owner, int attemptCount) {
}
