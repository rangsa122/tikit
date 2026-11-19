package com.tikit.member.dto;

// 회원 탈퇴 가능 여부 응답
public record MemberWithdrawalEligibilityResponseDTO(
        boolean eligible
) {
}