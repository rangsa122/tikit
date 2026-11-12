package com.tikit.performance.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record BookingStatusResponseDTO(
        Long performanceId,
        Summary summary,
        List<SessionStatus> sessions
) {
    // 공연 전체 합계
    public record Summary(
            long totalSeats,
            long soldSeats,
            long heldSeats,
            long bookingCount,
            long canceledCount,
            long revenue
    ) {
    }

    // 회차 하나의 현황
    public record SessionStatus(
            Long sessionId,
            OffsetDateTime startsAt,
            long totalSeats,
            long soldSeats,
            long heldSeats,
            long bookingCount,
            long canceledCount,
            long revenue
    ) {
    }
}