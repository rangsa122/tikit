package com.tikit.performance.dto.response;

import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.SalesStatus;
import com.tikit.performance.entity.Visibility;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record PerformancePublicationResponseDTO(
        Long performanceId,
        PublicationStatus publicationStatus,
        Visibility visibility,
        SalesStatus salesStatus,
        Long version,
        OffsetDateTime publishedAt,
        Long publishedBy
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static PerformancePublicationResponseDTO from(Performance p) {
        return new PerformancePublicationResponseDTO(
                p.getId(),
                p.getPublicationStatus(),
                p.getVisibility(),
                p.getSalesStatus(),
                p.getVersion(),
                p.getPublishedAt() == null
                        ? null
                        : p.getPublishedAt().atZone(KST).toOffsetDateTime(),
                p.getPublishedBy() == null
                        ? null
                        : p.getPublishedBy().getId()
        );
    }
}