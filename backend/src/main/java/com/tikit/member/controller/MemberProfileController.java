package com.tikit.member.controller;

import com.tikit.auth.cookie.RefreshTokenCookie;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.member.dto.MemberPasswordChangeRequestDTO;
import com.tikit.member.dto.MemberProfileResponseDTO;
import com.tikit.member.dto.MemberProfileUpdateRequestDTO;
import com.tikit.member.dto.MemberWithdrawalRequestDTO;
import com.tikit.member.dto.MemberWithdrawalEligibilityResponseDTO;
import com.tikit.member.service.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;

// 회원 정보 API 컨트롤러

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MemberProfileController {

    private final MemberProfileService memberProfileService;
    private final RefreshTokenCookie refreshTokenCookie;

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

    // 회원 탈퇴 API
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping("/withdrawal")
    public ResponseEntity<Void> withdrawMe(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody MemberWithdrawalRequestDTO request
    ) {
        // 비밀번호 및 탈퇴 제한 조건 확인 후 탈퇴
        memberProfileService.withdrawMyAccount(Long.parseLong(memberId), request.password());

        return ResponseEntity.noContent().build();
    }

    // 회원 탈퇴 가능 여부 조회 API
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/withdrawal-eligibility")
    public ResponseDTO<MemberWithdrawalEligibilityResponseDTO> getWithdrawalEligibility(
            @AuthenticationPrincipal String memberId
    ) {
        // 유효 예매 및 결제·환불 상태 확인
        MemberWithdrawalEligibilityResponseDTO eligibility = memberProfileService.getWithdrawalEligibility(Long.parseLong(memberId));

        return ResponseDTO.success("회원 탈퇴 가능 여부 조회 성공", eligibility);
    }

    // 회원 비밀번호 변경 API
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'ADMIN')")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody MemberPasswordChangeRequestDTO request
    ) {
        // 현재 비밀번호 확인 및 새 비밀번호 변경
        memberProfileService.changePassword(
                Long.parseLong(memberId),
                request.currentPassword(),
                request.newPassword()
        );

        // Refresh Token 쿠키 삭제
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.clear().toString()
                )
                .build();
    }
}
