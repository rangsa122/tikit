package com.tikit.admin.dto.request;

import com.tikit.auth.entity.MemberStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminMemberStatusChangeRequestDTO(
        @NotNull(message = "변경할 회원 상태는 필수입니다.")
        MemberStatus status,

        // 관리자가 조회한 시점의 회원 버전 (다른 관리자가 먼저 바꿨으면 409 VERSION_CONFLICT)
        @NotNull(message = "회원 버전은 필수입니다.")
        @PositiveOrZero(message = "회원 버전은 0 이상이어야 합니다.")
        Long expectedVersion,

        @NotBlank(message = "상태 변경 사유는 필수입니다.")
        @Size(max = 1000, message = "상태 변경 사유는 1000자 이내로 입력해야 합니다.")
        String reason
) {
}
