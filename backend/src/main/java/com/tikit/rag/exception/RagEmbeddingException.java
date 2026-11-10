package com.tikit.rag.exception;

/** 워커가 실패 상태를 기록하도록 전달하며 외부 응답 본문은 포함하지 않는다. */
public class RagEmbeddingException extends RuntimeException {
    public RagEmbeddingException(String message) {
        super(message);
    }
}
