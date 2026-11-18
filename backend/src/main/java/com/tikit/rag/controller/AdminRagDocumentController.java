package com.tikit.rag.controller;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.rag.dto.request.RagDocumentCreateRequest;
import com.tikit.rag.dto.response.RagDocumentDetailResponse;
import com.tikit.rag.dto.response.RagDocumentPageResponse;
import com.tikit.rag.exception.RagException;
import com.tikit.rag.service.RagDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/rag/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")//클래스 요청 처리 메서드에 ADMIN 권한 조건을 검 ->EnableMethodSecurity 설정과 연결됨
public class AdminRagDocumentController {

    private final RagDocumentService ragDocumentService;

    // 공통 정책 문서 등록
    @PostMapping
    public ResponseEntity<ResponseDTO<RagDocumentDetailResponse>> createDocument(
            @AuthenticationPrincipal String accountId, //jwt필터가 넣은 String 사용자 ID를 받음 요청 JSON으로 작성자를 받지 않아 다른 사람 ID를 임의로 제출하지 못하게함
            @Valid @RequestBody RagDocumentCreateRequest request //JSON요청을 DTO로 변환하고 제목,본문 제약을 검사함,request안에는 제목,본문이 들어있음
    ) {
        Long adminId = Long.valueOf(accountId);

        RagDocumentDetailResponse response =
                ragDocumentService.createPolicy(adminId, request);

        URI location = URI.create(
                "/api/v1/admin/rag/documents/" + response.id()
        );

        return ResponseEntity.created(location)
                .body(ResponseDTO.success("문서가 등록되었습니다.", response));
    }

    // 공통 정책 문서 목록 조회
    @GetMapping
    public ResponseEntity<ResponseDTO<RagDocumentPageResponse>> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RagException(ErrorCode.VALIDATION_ERROR);
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        RagDocumentPageResponse response = RagDocumentPageResponse.from(
                ragDocumentService.getDocuments(pageable)
        );

        return ResponseEntity.ok(
                ResponseDTO.success("문서 목록을 조회했습니다.", response)
        );
    }

    // 공통 정책 문서 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<RagDocumentDetailResponse>> getDocument(
            @PathVariable("id") Long documentId
    ) {
        if (documentId <= 0) {
            throw new RagException(ErrorCode.VALIDATION_ERROR);
        }

        RagDocumentDetailResponse response =
                ragDocumentService.getDocument(documentId);

        return ResponseEntity.ok(
                ResponseDTO.success("문서 상세 내용을 조회했습니다.", response)
        );
    }
}
