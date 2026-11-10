package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.KopisPerformanceDetailResponseDTO;
import com.tikit.performance.dto.response.KopisPerformanceSearchResponseDTO;
import com.tikit.performance.service.KopisSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/manager/kopis")
@RequiredArgsConstructor
public class KopisController {

    private final KopisSearchService service;

    @GetMapping("/performances")
    public ResponseEntity<ResponseDTO<KopisPerformanceSearchResponseDTO>> searchKopisPerformances(
            @AuthenticationPrincipal String memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String genreCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ResponseDTO.success("KOPIS 공연 검색에 성공했습니다",
                service.search(Long.valueOf(memberId), startDate, endDate, title, genreCode, page, size)));
    }

    @GetMapping("/performances/{kopisId}")
    public ResponseEntity<ResponseDTO<KopisPerformanceDetailResponseDTO>> getKopisPerformance(
            @AuthenticationPrincipal String memberId,
            @PathVariable String kopisId) {
        return ResponseEntity.ok(ResponseDTO.success("KOPIS 공연 상세 조회에 성공했습니다",
                service.getDetail(Long.valueOf(memberId), kopisId)));
    }
}