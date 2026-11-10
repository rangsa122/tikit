
package com.tikit.performance.dto.response;

import com.tikit.performance.entity.Performance;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FavoritePerformanceResponseDTO(
        String performanceId,
        String title,
        String genreCode,
        String venueName,
        String posterUrl,
        LocalDate startDate,
        LocalDate endDate,
        Long minPrice,
        String salesStatus,
        OffsetDateTime nextSalesOpenAt
) {

    public static FavoritePerformanceResponseDTO from(
            Performance performance,
            Long minPrice,
            OffsetDateTime nextSalesOpenAt
    ) {
        return new FavoritePerformanceResponseDTO(
                performance.getId().toString(),
                performance.getTitle(),
                performance.getGenre().getCode(),
                performance.getVenueName(),
                performance.getPosterUrl(),
                performance.getStartDate(),
                performance.getEndDate(),
                minPrice,
                performance.getSalesStatus().name(),
                nextSalesOpenAt
        );
    }
}
