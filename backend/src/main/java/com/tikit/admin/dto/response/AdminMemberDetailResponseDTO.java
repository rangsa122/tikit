package com.tikit.admin.dto.response;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.admin.entity.AuditLog;

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
        Instant withdrawnAt,
        String suspensionReason,
        Instant suspendedAt,
        Long suspendedBy
) {
    public static AdminMemberDetailResponseDTO from(Member member) {
        return from(member, null);
    }

    public static AdminMemberDetailResponseDTO from(Member member, AuditLog latestStatusChange) {
        boolean suspended = member.getStatus() == MemberStatus.SUSPENDED
                && latestStatusChange != null && "MEMBER_SUSPEND".equals(latestStatusChange.getAction());
        return new AdminMemberDetailResponseDTO(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                member.getVersion(),
                member.getCreatedAt(),
                member.getUpdatedAt(),
                member.getWithdrawnAt(),
                suspended ? latestStatusChange.getReason() : null,
                suspended ? latestStatusChange.getCreatedAt() : null,
                suspended ? latestStatusChange.getActorMemberId() : null
        );
    }
}
