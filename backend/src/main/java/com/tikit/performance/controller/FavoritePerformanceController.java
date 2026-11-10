
package com.tikit.performance.controller;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.PageResponseDTO;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.FavoritePerformanceResponseDTO;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.service.FavoritePerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FavoritePerformanceController {

    private final FavoritePerformanceService favoritePerformanceService;

    // 관심 공연 목록 조회
    @GetMapping
    public ResponseEntity<ResponseDTO<PageResponseDTO<FavoritePerformanceResponseDTO>>> getFavorites(
            @AuthenticationPrincipal String memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<FavoritePerformanceResponseDTO> favorites =
                favoritePerformanceService.getFavorites(
                        parseMemberId(memberId),
                        page,
                        size
                );

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "관심 공연 목록 조회에 성공했습니다.",
                        PageResponseDTO.from(favorites)
                )
        );
    }

    // 관심 공연 등록
    @PutMapping("/{performanceId}")
    public ResponseEntity<ResponseDTO<FavoriteRegistrationResponseDTO>> addFavorite(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long performanceId
    ) {
        favoritePerformanceService.addFavorite(
                parseMemberId(memberId),
                performanceId
        );

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "관심 공연이 등록되었습니다.",
                        new FavoriteRegistrationResponseDTO(
                                performanceId.toString(),
                                true
                        )
                )
        );
    }

    // 관심 공연 해제
    @DeleteMapping("/{performanceId}")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long performanceId
    ) {
        favoritePerformanceService.removeFavorite(
                parseMemberId(memberId),
                performanceId
        );

        return ResponseEntity.noContent().build();
    }

    // 로그인한 회원 ID 변환
    private Long parseMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new PerformanceException(
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
        }

        try {
            return Long.valueOf(memberId);
        } catch (NumberFormatException exception) {
            throw new PerformanceException(
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
        }
    }

    // 관심 공연 등록 응답
    public record FavoriteRegistrationResponseDTO(
            String performanceId,
            boolean favorited
    ) {
    }
}
