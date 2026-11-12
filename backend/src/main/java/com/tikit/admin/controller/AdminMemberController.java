package com.tikit.admin.controller;

import com.tikit.admin.dto.request.AdminMemberSearchRequestDTO;
import com.tikit.admin.dto.request.AdminMemberRoleChangeRequestDTO;
import com.tikit.admin.dto.request.AdminMemberStatusChangeRequestDTO;
import com.tikit.admin.dto.response.AdminMemberDetailResponseDTO;
import com.tikit.admin.dto.response.AdminMemberListResponseDTO;
import com.tikit.admin.dto.response.AdminMemberSummaryResponseDTO;
import com.tikit.admin.service.AdminMemberService;
import com.tikit.common.dto.ResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/members")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ResponseDTO<AdminMemberSummaryResponseDTO>> changeRole(
            @AuthenticationPrincipal String actorId,
            @PathVariable @Positive Long memberId,
            @Valid @RequestBody AdminMemberRoleChangeRequestDTO request
    ) {
        return ResponseEntity.ok(ResponseDTO.success("회원 권한 변경 성공",
                adminMemberService.changeRole(Long.parseLong(actorId), memberId, request)));
    }

    // 회원 운영 목록
    @GetMapping
    public ResponseEntity<ResponseDTO<AdminMemberListResponseDTO>> getMembers(
            @Valid @ModelAttribute AdminMemberSearchRequestDTO request
    ) {
        return ResponseEntity.ok(
                ResponseDTO.success("회원 목록 조회 성공", adminMemberService.getMembers(request)));
    }

    // 회원 상세
    @GetMapping("/{memberId}")
    public ResponseEntity<ResponseDTO<AdminMemberDetailResponseDTO>> getMember(
            @PathVariable @Positive(message = "회원 ID는 1 이상이어야 합니다.") Long memberId
    ) {
        return ResponseEntity.ok(
                ResponseDTO.success("회원 상세 조회 성공", adminMemberService.getMember(memberId)));
    }

    // 회원 이용 제한 또는 해제
    @PatchMapping("/{memberId}/status")
    public ResponseEntity<ResponseDTO<AdminMemberSummaryResponseDTO>> changeStatus(
            @AuthenticationPrincipal String actorId,
            @PathVariable @Positive(message = "회원 ID는 1 이상이어야 합니다.") Long memberId,
            @Valid @RequestBody AdminMemberStatusChangeRequestDTO request
    ) {
        return ResponseEntity.ok(
                ResponseDTO.success("회원 상태 변경 성공",
                        adminMemberService.changeStatus(Long.parseLong(actorId), memberId, request)));
    }
}
