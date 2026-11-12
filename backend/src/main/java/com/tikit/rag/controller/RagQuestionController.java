package com.tikit.rag.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.rag.dto.request.RagQuestionRequest;
import com.tikit.rag.dto.response.RagQuestionResponse;
import com.tikit.rag.service.RagQuestionAnswerer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag/questions")
public class RagQuestionController {

    private final RagQuestionAnswerer questionService;

    public RagQuestionController(RagQuestionAnswerer questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<ResponseDTO<RagQuestionResponse>> receiveQuestion(
            @Valid @RequestBody RagQuestionRequest request
    ) {
        return ResponseEntity.ok(ResponseDTO.success(
                "AI 안내 답변 생성 성공", questionService.answer(request.question())));
    }
}
