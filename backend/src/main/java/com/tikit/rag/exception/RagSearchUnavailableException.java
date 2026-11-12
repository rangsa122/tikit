package com.tikit.rag.exception;

public class RagSearchUnavailableException extends RuntimeException {
    public RagSearchUnavailableException() {
        super("RAG 검색을 사용할 수 없습니다.");
    }
}
