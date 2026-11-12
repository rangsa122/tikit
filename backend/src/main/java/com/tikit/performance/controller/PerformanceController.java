package com.tikit.performance.controller;

import com.tikit.common.dto.PageResponseDTO;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.request.*;
import com.tikit.performance.dto.response.PerformancePublicationResponseDTO;
import com.tikit.performance.dto.response.PerformanceResponseDTO;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.service.PerformancePublishFacade;
import com.tikit.performance.service.PerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manager/performances")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService service;
    private final PerformancePublishFacade publishFacade;

    @PostMapping
    public ResponseEntity<ResponseDTO<PerformanceResponseDTO>> createPerformance(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody PerformanceCreateRequestDTO dto){
        PerformanceResponseDTO data = service.create(Long.valueOf(memberId), dto);
        return  ResponseEntity.status(HttpStatus.CREATED).body(ResponseDTO.success("공연이 등록되었습니다.", data));
    }

    @GetMapping
    public ResponseEntity<ResponseDTO<PageResponseDTO<PerformanceResponseDTO>>> listManagerPerformances(
            @AuthenticationPrincipal String memberId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false)PublicationStatus publicationStatus){
        return  ResponseEntity.ok(ResponseDTO.success(
                "공연 목록 조회에 성공했습니다",
                PageResponseDTO.from(service.list(Long.valueOf(memberId), publicationStatus, page, size))));
    }

    @GetMapping("/{performanceId}")
    public ResponseEntity<ResponseDTO<PerformanceResponseDTO>> getManagerPerformance(
            @AuthenticationPrincipal String memberId, @PathVariable Long performanceId){
        return ResponseEntity.ok(ResponseDTO.success("공연조회에 성공했습니다",
                service.get(Long.valueOf(memberId),performanceId)));
    }

    @PatchMapping("/{performanceId}")
    public ResponseEntity<ResponseDTO<PerformanceResponseDTO>> updatePerformance(
            @AuthenticationPrincipal String memberId, @PathVariable Long performanceId,
            @Valid @RequestBody PerformanceUpdateRequestDTO request){
        return ResponseEntity.ok(ResponseDTO.success("공연정보가 수정되었습니다",
                service.update(Long.valueOf(memberId), performanceId, request)));
    }

    @PatchMapping("/{performanceId}/sales-status")
    public ResponseEntity<ResponseDTO<PerformanceResponseDTO>> changeSalesStatus(
            @AuthenticationPrincipal String memberId, @PathVariable Long performanceId,
            @Valid @RequestBody SalesStatusUpdateRequestDTO request){
        return ResponseEntity.ok(ResponseDTO.success("신규 판매 상태가 변경되었습니다",
                service.changeSalesStatus(Long.valueOf(memberId), performanceId, request)));
    }

    @PatchMapping("/{performanceId}/visibility")
    public ResponseEntity<ResponseDTO<PerformancePublicationResponseDTO>> changeVisibility(
            @AuthenticationPrincipal String memberId, @PathVariable Long performanceId,
            @Valid @RequestBody VisibilityUpdateRequestDTO request){
        return ResponseEntity.ok(ResponseDTO.success("공개 상태가 변경되었습니다",
                service.changeVisibility(Long.valueOf(memberId), performanceId, request)));
    }

    @PostMapping("/{performanceId}/publication")
    public ResponseEntity<ResponseDTO<PerformancePublicationResponseDTO>> publishPerformance(
            @AuthenticationPrincipal String memberId, @PathVariable Long performanceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PerformancePublishRequestDTO request){
        return ResponseEntity.ok(ResponseDTO.success("공연이 게시되었습니다",
                publishFacade.publish(Long.valueOf(memberId), performanceId,
                        request.getExpectedVersion(), idempotencyKey)));
    }
}

