package com.tikit.member.dto;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.auth.entity.MemberStatus;
import java.time.Instant;

// 회원 정보 조회 응답 DTO

public record MemberProfileResponseDTO(
        Long id,
        String email,
        String name,
        boolean notificationEnabled,
        Instant createdAt,
        Role role,
        MemberStatus accountStatus
) {
    public static MemberProfileResponseDTO from(Member member) {
        return new MemberProfileResponseDTO(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.isNotificationEnabled(),
                member.getCreatedAt(),
                member.getRole(),
                member.getStatus()
        );
    }
}
