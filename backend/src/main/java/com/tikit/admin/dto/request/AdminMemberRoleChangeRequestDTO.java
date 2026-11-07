package com.tikit.admin.dto.request;

import com.tikit.auth.entity.Role;
import jakarta.validation.constraints.*;

public record AdminMemberRoleChangeRequestDTO(
        @NotNull Role role,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 1000) String reason
) {
    @AssertTrue(message = "MEMBER 또는 MANAGER 권한으로만 변경할 수 있습니다.")
    public boolean isAllowedRole() {
        return role == Role.MEMBER || role == Role.MANAGER;
    }
}
