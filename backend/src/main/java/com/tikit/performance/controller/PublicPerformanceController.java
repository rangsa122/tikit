
package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.PerformanceResponseDTO;
import com.tikit.performance.dto.PerformanceSessionResponseDTO;
import com.tikit.performance.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 일반 사용자용 공연 조회 API
 */
@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
public class PublicPerformanceController {

    private final PerformanceService performanceService;

    /**
     * 공개 공연 상세 조회
     *
     * GET /api/v1/performances/{performanceId}
     */
    @GetMapping("/{performanceId}")
    public ResponseEntity<ResponseDTO<PerformanceResponseDTO>> getPerformanceDetail(
            @PathVariable Long performanceId
    ) {
        PerformanceResponseDTO data =
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
     *
     * GET /api/v1/performances/{performanceId}/sessions
     */
    @GetMapping("/{performanceId}/sessions")
    public ResponseEntity<ResponseDTO<List<PerformanceSessionResponseDTO>>> getPerformanceSessions(
            @PathVariable Long performanceId
    ) {
        List<PerformanceSessionResponseDTO> data =
                performanceService.getPublicSessions(performanceId);

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "공연 회차 목록 조회에 성공했습니다.",
                        data
                )
        );
    }
}
