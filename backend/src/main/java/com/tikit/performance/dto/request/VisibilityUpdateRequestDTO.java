package com.tikit.performance.dto.request;

import com.tikit.performance.entity.Visibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VisibilityUpdateRequestDTO {

    @NotNull(message = "공개 상태는 필수 입력 항목입니다.")
    private Visibility visibility;

    @NotNull(message = "expectedVersion은 필수 입력 항목입니다.")
    @Min(value = 1, message = "expectedVersion은 1 이상이어야 합니다.")
    private Long expectedVersion;

    @Size(max = 1000, message = "사유는 1000자 이내로 입력해야 합니다.")
    private String reason;
}