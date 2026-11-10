package com.tikit.performance.dto.response;

import java.time.LocalDate;

public record KopisPerformanceItemDTO(
        String kopisId,
        String title,
        String venueName,
        LocalDate startDate,
        LocalDate endDate,
        String posterUrl,
        String area,
        String genreName,
        String state,
        boolean alreadyRegistered //이미 등록된 kopisId인지
) {
}