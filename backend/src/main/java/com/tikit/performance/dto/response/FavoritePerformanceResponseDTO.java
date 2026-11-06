
package com.tikit.performance.dto.response;

import com.tikit.performance.entity.Performance;

public record FavoritePerformanceResponseDTO(
        Long id,
        String title,
        String venue,
        String period,
        String posterUrl
) {

    public static FavoritePerformanceResponseDTO from(
            Performance performance
    ) {
        return new FavoritePerformanceResponseDTO(
                performance.getId(),
                performance.getTitle(),
                performance.getVenueName(),
                performance.getStartDate() + " ~ " + performance.getEndDate(),
                performance.getPosterUrl()
        );
    }
}
