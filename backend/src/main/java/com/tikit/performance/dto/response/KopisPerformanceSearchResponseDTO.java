package com.tikit.performance.dto.response;

import java.util.List;

public record KopisPerformanceSearchResponseDTO(
        List<KopisPerformanceItemDTO> items,
        int page,
        int size
) {
}