package com.tikit.performance.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetDateTime;

public record PublicPerformanceSummaryDTO(
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
}
