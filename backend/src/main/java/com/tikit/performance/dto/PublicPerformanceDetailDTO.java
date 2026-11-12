package com.tikit.performance.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PublicPerformanceDetailDTO(
        String performanceId,
        String title,
        String genreCode,
        String venueName,
        String posterUrl,
        LocalDate startDate,
        LocalDate endDate,
        Long minPrice,
        String salesStatus,
        OffsetDateTime nextSalesOpenAt,
        String description,
        String kopisId,
        List<SaleOpeningItem> saleOpenings,
        Integer bookingLimitPerMember,
        String cancellationPolicy
) {
    public record SaleOpeningItem(
            String openingId,
            OffsetDateTime salesOpenAt,
            boolean notified
    ) {
    }
}