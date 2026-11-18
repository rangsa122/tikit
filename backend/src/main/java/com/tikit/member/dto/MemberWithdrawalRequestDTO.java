package com.tikit.member.dto;

import jakarta.validation.constraints.NotBlank;

// 회원 탈퇴 시 비밀번호 확인 요청
public record MemberWithdrawalRequestDTO(
        @NotBlank String password
) {
}