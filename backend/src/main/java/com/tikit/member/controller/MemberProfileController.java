package com.tikit.member.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.member.dto.MemberProfileResponseDTO;
import com.tikit.member.dto.MemberProfileUpdateRequestDTO;
import com.tikit.member.service.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

// 회원 정보 API 컨트롤러

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseDTO<MemberProfileResponseDTO> getMe(
            @AuthenticationPrincipal String memberId
    ) {
        MemberProfileResponseDTO profile =
                memberProfileService.getMyProfile(Long.parseLong(memberId));

        return ResponseDTO.success("회원정보 조회 성공", profile);
    }

    // 회원정보 수정 API - 이름 변경
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'ADMIN')")
    @PatchMapping
    public ResponseDTO<MemberProfileResponseDTO> updateMe(

            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody MemberProfileUpdateRequestDTO request
    ) {

        Long id = Long.parseLong(memberId);

        MemberProfileResponseDTO profile =
                memberProfileService.updateMyProfile(id, request);
        
        return ResponseDTO.success("회원정보 수정 성공", profile);
    }

}