
package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.PublicPerformanceDetailDTO;
import com.tikit.performance.dto.PublicPerformancePageDTO;
import com.tikit.performance.dto.PerformanceSessionListDTO;
import com.tikit.performance.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
public class PublicPerformanceController {

    private final PerformanceService performanceService;

    /**
     * 공개 공연 목록 검색
     * GET /api/v1/performances
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<PublicPerformancePageDTO>>
    getPublicPerformances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String genreCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "startDate") String sort
    ) {

        PublicPerformancePageDTO data =
                performanceService.getPublicPerformances(
                        page,
                        size,
                        keyword,
                        genreCode,
                        fromDate,
                        toDate,
                        sort
                );

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "공개 공연 목록 조회에 성공했습니다.",
                        data
                )
        );
    }

    /**
     * 공개 공연 상세 조회
     * GET /api/v1/performances/{performanceId}
     */
    @GetMapping("/{performanceId}")
    public ResponseEntity<ResponseDTO<PublicPerformanceDetailDTO>>
    getPerformanceDetail(@PathVariable Long performanceId) {

        PublicPerformanceDetailDTO data =
                performanceService.getPublicDetail(performanceId);

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "공연 상세 조회에 성공했습니다.",
                        data
                )
        );
    }

    /**
     * 공개 공연 회차 목록 조회
     * GET /api/v1/performances/{performanceId}/sessions
     */
    @GetMapping("/{performanceId}/sessions")
    public ResponseEntity<ResponseDTO<PerformanceSessionListDTO>>
    getPerformanceSessions(@PathVariable Long performanceId) {

        PerformanceSessionListDTO data =
                performanceService.getPublicSessions(performanceId);

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "공연 회차 목록 조회에 성공했습니다.",
                        data
                )
        );
    }
}
