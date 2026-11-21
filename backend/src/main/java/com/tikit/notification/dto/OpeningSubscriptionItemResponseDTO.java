package com.tikit.notification.dto;

import java.time.Instant;
import java.time.LocalDate;

// 공연별 알림 설정 목록 응답
public record OpeningSubscriptionItemResponseDTO(
        Long performanceId,
        String title,
        String venueName,
        LocalDate startDate,
        LocalDate endDate,
        Instant opensAt
) {
}
