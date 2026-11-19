package com.tikit.performance.dto.response;

import com.tikit.performance.entity.Performance;

/**
 * 홈 배너 한 칸에 필요한 정보.
 * 클릭하면 공연 상세로 이동하므로 performanceId를 함께 내려준다.
 */
public record BannerResponseDTO(
        String performanceId,
        String title,
        String bannerUrl
) {
    public static BannerResponseDTO from(Performance performance) {
        return new BannerResponseDTO(
                String.valueOf(performance.getId()),
                performance.getTitle(),
                performance.getBannerUrl()
        );
    }
}