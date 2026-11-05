package com.tikit.performance.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PerformanceSessionResponseDTO(
        String sessionId,
        String performanceId,
        OffsetDateTime startsAt,
        OffsetDateTime salesOpenAt,
        OffsetDateTime salesCloseAt,
        OffsetDateTime cancellationDeadline,
        String bookingAvailability,
        List<GradePrice> gradePrices,
        OffsetDateTime serverTime
) {
    public record GradePrice(
            String grade,
            Long price
    ) {
    }
}