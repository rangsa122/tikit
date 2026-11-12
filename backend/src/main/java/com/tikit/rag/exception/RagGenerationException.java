package com.tikit.rag.exception;

/** 외부 모델의 응답 본문이나 입력 문서를 오류 응답에 포함하지 않는다. */
public class RagGenerationException extends RuntimeException {
    public RagGenerationException() {
        super("Ollama 답변 생성에 실패했습니다.");
    }
}
