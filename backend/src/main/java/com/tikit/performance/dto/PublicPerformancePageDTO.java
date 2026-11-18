package com.tikit.performance.dto;

import java.util.List;

public record PublicPerformancePageDTO(
        List<PublicPerformanceSummaryDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}