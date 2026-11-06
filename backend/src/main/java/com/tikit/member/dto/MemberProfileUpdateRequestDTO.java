package com.tikit.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 회원정보 수정 요청 DTO

public record MemberProfileUpdateRequestDTO(

        // 이름 공백 입력 방지
        @NotBlank(message = "이름은 필수 입력 항목입니다.")

        @Size(max = 50, message = "이름은 50자 이내로 입력해야 합니다.")

        String name
) {
}