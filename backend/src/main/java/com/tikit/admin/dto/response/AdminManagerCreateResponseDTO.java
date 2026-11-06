package com.tikit.admin.dto.response;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;

import java.time.Instant;

public record AdminManagerCreateResponseDTO(
        Long memberId,
        String email,
        String name,
        Role role,
        MemberStatus accountStatus,
        Long version,
        Instant createdAt
) {
    public static AdminManagerCreateResponseDTO from(Member member) {
        return new AdminManagerCreateResponseDTO(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                member.getVersion(),
                member.getCreatedAt()
        );
    }
}
