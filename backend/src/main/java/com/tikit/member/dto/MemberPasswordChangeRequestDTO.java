package com.tikit.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 비밀번호 변경 요청 DTO
public record MemberPasswordChangeRequestDTO(

        @NotBlank(message = "현재 비밀번호는 필수 입력 항목입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수 입력 항목입니다.")
        @Size(min = 10, max = 128,
                message = "비밀번호는 10자 이상 128자 이하로 입력해야 합니다.")
        String newPassword
) {
}
