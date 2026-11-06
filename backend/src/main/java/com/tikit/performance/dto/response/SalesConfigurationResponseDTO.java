package com.tikit.performance.dto.response;

import com.tikit.performance.dto.SessionConfigurationDTO;

import java.util.List;

public record SalesConfigurationResponseDTO(
        Long performanceId,
        Long version,
        Integer bookingLimitPerMember,
        List<SessionConfigurationDTO> sessions
) {
}
