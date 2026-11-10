package com.tikit.rag.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RagQuestionRequest(
        @NotBlank(message = "질문을 입력해 주세요.") String question
) {
}
