package com.tikit.performance.dto.response;

import java.time.LocalDate;

public record KopisPerformanceDetailResponseDTO(
        String kopisId,
        String title,
        String genreCode,       // 매핑 실패 시 null
        String genreName,       // KOPIS 원본 장르명
        String venueName,
        String organizerName,
        String description,
        String posterUrl,
        LocalDate startDate,
        LocalDate endDate,
        String cast,
        String runtime,
        String age,
        String timeGuidance,
        String priceGuidance,
        String state,
        boolean alreadyRegistered
) {
}