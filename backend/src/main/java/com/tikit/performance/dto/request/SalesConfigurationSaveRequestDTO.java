package com.tikit.performance.dto.request;

import com.tikit.performance.dto.SessionConfigurationDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SalesConfigurationSaveRequestDTO(
        @NotNull
        @Min(0)
        Long expectedVersion,

        @NotNull
        @Min(1)
        @Max(10)
        Integer bookingLimitPerMember,

        @NotNull
        @Size(min = 1, max = 50)
        @Valid
        List<SessionConfigurationDTO> sessions
) {
}
