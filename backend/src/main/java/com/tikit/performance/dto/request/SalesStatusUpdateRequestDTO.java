package com.tikit.performance.dto.request;

import com.tikit.performance.entity.SalesStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SalesStatusUpdateRequestDTO {

    @NotNull(message = "신규 판매 상태는 필수 입력 항목입니다.")
    private SalesStatus salesStatus;

    @NotNull(message = "expectedVersion은 필수 입력 항목입니다.")
    @Min(value = 1, message = "expectedVersion은 1 이상이어야 합니다.")
    private Long expectedVersion;
}
