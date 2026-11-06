package com.tikit.performance.dto;

import com.tikit.performance.dto.response.PerformanceSessionResponseDTO;

import java.util.List;

public record PerformanceSessionListDTO(
        List<PerformanceSessionResponseDTO> items
) {
}