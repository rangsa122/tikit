package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.BannerResponseDTO;
import com.tikit.performance.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 홈 화면 배너 조회. 로그인 없이 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final PerformanceService performanceService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<BannerResponseDTO>>> getBanners() {
        return ResponseEntity.ok(ResponseDTO.success(
                "배너 목록 조회 성공",
                performanceService.getBanners()));
    }
}