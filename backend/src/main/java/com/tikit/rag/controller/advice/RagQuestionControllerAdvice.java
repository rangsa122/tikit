package com.tikit.rag.controller.advice;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.rag.controller.RagQuestionController;
import com.tikit.rag.exception.RagEmbeddingException;
import com.tikit.rag.exception.RagGenerationException;
import com.tikit.rag.exception.RagSearchUnavailableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RagQuestionController.class)
public class RagQuestionControllerAdvice {
    @ExceptionHandler({RagEmbeddingException.class, RagSearchUnavailableException.class, RagGenerationException.class})
    public ResponseEntity<ResponseDTO<Void>> handleUnavailable(RuntimeException exception) {
        var error = ErrorCode.RAG_UNAVAILABLE;
        return ResponseEntity.status(error.getStatus()).body(ResponseDTO.fail(error));
    }
}
