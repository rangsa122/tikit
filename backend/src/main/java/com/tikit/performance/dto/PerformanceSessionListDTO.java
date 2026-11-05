package com.tikit.performance.dto;

import java.util.List;

public record PerformanceSessionListDTO(
        List<PerformanceSessionResponseDTO> items
) {
}