package com.tikit.performance.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PerformancePublishRequestDTO {

    @NotNull(message = "expectedVersion은 필수 입력 항목입니다.")
    @Min(value = 0, message = "expectedVersion은 0 이상이어야 합니다.")
    private Long expectedVersion;
}