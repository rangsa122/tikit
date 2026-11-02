package com.tikit.admin.service;

import com.tikit.admin.dto.request.AdminMemberSearchRequestDTO;
import com.tikit.admin.dto.request.AdminMemberStatusChangeRequestDTO;
import com.tikit.admin.dto.response.AdminMemberDetailResponseDTO;
import com.tikit.admin.dto.response.AdminMemberListResponseDTO;
import com.tikit.admin.dto.response.AdminMemberSummaryResponseDTO;
import com.tikit.admin.exception.AdminMemberNotFoundException;
import com.tikit.admin.entity.AuditLog;
import com.tikit.admin.repository.AuditLogRepository;
import com.tikit.admin.repository.AdminMemberSpecifications;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminMemberListResponseDTO getMembers(AdminMemberSearchRequestDTO request) {
        return AdminMemberListResponseDTO.from(memberRepository.findAll(
                AdminMemberSpecifications.search(request.getKeyword(), request.getStatus(), request.getRole()),
                request.toPageable()
        ));
    }

    public AdminMemberDetailResponseDTO getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .map(AdminMemberDetailResponseDTO::from)
                .orElseThrow(AdminMemberNotFoundException::new);
    }

    @Transactional
    public AdminMemberSummaryResponseDTO changeStatus(Long actorId, Long memberId,
                                                     AdminMemberStatusChangeRequestDTO request) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new MemberStatusChangeException(ErrorCode.ACCESS_DENIED));
        if (actor.getRole() != Role.ADMIN) {
            throw new MemberStatusChangeException(ErrorCode.ACCESS_DENIED);
        }
        if (actor.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberStatusChangeException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        if (actorId.equals(memberId)) {
            throw new MemberStatusChangeException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(AdminMemberNotFoundException::new);

        // 조회 이후 다른 관리자가 먼저 바꿨으면 거부
        if (!member.getVersion().equals(request.expectedVersion())) {
            throw new MemberStatusChangeException(ErrorCode.VERSION_CONFLICT);
        }

        MemberStatus before = member.getStatus();
        if (request.status() == MemberStatus.SUSPENDED) {
            member.suspend();
        } else if (request.status() == MemberStatus.ACTIVE) {
            member.resume();
        } else {
            throw new MemberStatusChangeException(ErrorCode.STATE_CONFLICT);
        }

        // 상태 변경과 감사 이력은 같은 트랜잭션에서 저장하거나 함께 롤백합니다.
        auditLogRepository.saveAndFlush(AuditLog.memberStatusChange(
                actorId, memberId, request.reason(), before, member.getStatus()));

        // 정지하면 저장된 리프레시 토큰을 지워 활성 세션을 무효화
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            refreshTokenRepository.delete(memberId.toString());
        }

        return AdminMemberSummaryResponseDTO.from(member);
    }
}
