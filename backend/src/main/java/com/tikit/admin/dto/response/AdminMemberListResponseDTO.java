package com.tikit.admin.dto.response;

import com.tikit.auth.entity.Member;
import org.springframework.data.domain.Page;

import java.util.List;

public record AdminMemberListResponseDTO(
        List<AdminMemberSummaryResponseDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AdminMemberListResponseDTO from(Page<Member> members) {
        return new AdminMemberListResponseDTO(
                members.getContent().stream()
                        .map(AdminMemberSummaryResponseDTO::from)
                        .toList(),
                members.getNumber(),
                members.getSize(),
                members.getTotalElements(),
                members.getTotalPages()
        );
    }
}
