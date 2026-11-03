
package com.tikit.performance.dto;

import com.tikit.performance.entity.PerformanceSession;

import java.time.Instant;

/**
 * 일반 사용자용 공연 회차 조회 응답 DTO
 */
public record PerformanceSessionResponseDTO(
        Long sessionId,
        Long performanceId,
        Instant startsAt,
        Instant salesCloseAt
) {

    /**
     * PerformanceSession 엔티티를 응답 DTO로 변환
     */
    public static PerformanceSessionResponseDTO from(
            PerformanceSession session
    ) {
        return new PerformanceSessionResponseDTO(
                session.getId(),
                session.getPerformance().getId(),
                session.getStartsAt(),
                session.getSalesCloseAt()
        );
    }
}
