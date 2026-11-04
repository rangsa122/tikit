
package com.tikit.performance.controller;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.service.FavoritePerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FavoritePerformanceController {

    private final FavoritePerformanceService favoritePerformanceService;

    @PutMapping("/{performanceId}")
    public ResponseEntity<ResponseDTO<Void>> addFavorite(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long performanceId
    ) {
        favoritePerformanceService.addFavorite(
                parseMemberId(memberId),
                performanceId
        );

        return ResponseEntity.ok(
                ResponseDTO.<Void>success(
                        "관심 공연이 등록되었습니다.",
                        null
                )
        );
    }

    @DeleteMapping("/{performanceId}")
    public ResponseEntity<ResponseDTO<Void>> removeFavorite(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long performanceId
    ) {
        favoritePerformanceService.removeFavorite(
                parseMemberId(memberId),
                performanceId
        );

        return ResponseEntity.ok(
                ResponseDTO.<Void>success(
                        "관심 공연이 해제되었습니다.",
                        null
                )
        );
    }

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
}
