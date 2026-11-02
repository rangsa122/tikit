package com.tikit.admin.dto.response;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;

// API 명세의 AdminMember (목록 항목과 상태 변경 응답)
public record AdminMemberSummaryResponseDTO(
        Long memberId,
        String email,
        String name,
        Role role,
        MemberStatus accountStatus,
        Long version
) {
    public static AdminMemberSummaryResponseDTO from(Member member) {
        return new AdminMemberSummaryResponseDTO(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                member.getVersion()
        );
    }
}
