package com.tikit.rag.service;

import com.tikit.rag.dto.response.RagQuestionResponse;

public interface RagQuestionAnswerer {
    RagQuestionResponse answer(String question);
}
