package com.tikit.admin.dto.response;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;

import java.time.Instant;

public record AdminMemberDetailResponseDTO(
        Long memberId,
        String email,
        String name,
        Role role,
        MemberStatus accountStatus,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        Instant withdrawnAt
) {
    public static AdminMemberDetailResponseDTO from(Member member) {
        return new AdminMemberDetailResponseDTO(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                member.getVersion(),
                member.getCreatedAt(),
                member.getUpdatedAt(),
                member.getWithdrawnAt()
        );
    }
}
