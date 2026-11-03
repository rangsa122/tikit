package com.tikit.performance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record SessionConfigurationDTO(
        Long sessionId,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime salesOpenAt,
        @NotNull OffsetDateTime salesCloseAt,
        @NotNull OffsetDateTime cancellationDeadline,
        @NotNull Long seatTemplateId,
        @NotNull @Size(min = 1) @Valid List<GradePriceDTO> gradePrices
        ) {
}
