package com.tikit.rag.exception;

public class RagSearchUnavailableException extends RuntimeException {
    public RagSearchUnavailableException() {
        super("검증용 RAG 검색을 사용할 수 없습니다.");
    }
}
