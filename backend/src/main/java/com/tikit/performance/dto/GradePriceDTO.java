package com.tikit.performance.dto;

import jakarta.validation.constraints.*;

public record GradePriceDTO(
        @NotBlank
        @Size(max = 20)
        String grade,

        @NotNull
        @Min(0)
        @Max(100_000_000)
        Long price
) {
}
