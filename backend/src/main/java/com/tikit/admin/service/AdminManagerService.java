package com.tikit.admin.service;

import com.tikit.admin.dto.request.AdminManagerCreateRequestDTO;
import com.tikit.admin.dto.response.AdminManagerCreateResponseDTO;
import com.tikit.admin.entity.AuditLog;
import com.tikit.admin.repository.AuditLogRepository;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.exception.AccountRestrictedException;
import com.tikit.auth.exception.DuplicateEmailException;
import com.tikit.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminManagerService {

    private final MemberRepository memberRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminManagerCreateResponseDTO createManager(Long actorId, AdminManagerCreateRequestDTO request) {
        // 토큰 발급 이후 변경된 역할과 상태도 현재 DB 기준으로 재검사합니다.
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new AccessDeniedException("관리자 계정이 존재하지 않습니다."));
        if (actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("ADMIN만 MANAGER 계정을 생성할 수 있습니다.");
        }
        if (actor.getStatus() != MemberStatus.ACTIVE) {
            throw new AccountRestrictedException("활성 ADMIN만 MANAGER 계정을 생성할 수 있습니다.");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }

        Member manager = Member.create(email, passwordEncoder.encode(request.getPassword()),
                request.getName(), Role.MANAGER);
        Member saved;
        try {
            saved = memberRepository.saveAndFlush(manager);
        } catch (DataIntegrityViolationException exception) {
            // 사전 검사 이후 동시 생성된 이메일도 유니크 제약으로 차단합니다.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uq_members_email".equals(constraint.getConstraintName())) {
                    throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
                }
            }
            throw exception;
        }

        // 감사 로그 저장 실패 시 신규 계정도 같은 트랜잭션에서 롤백됩니다.
        auditLogRepository.saveAndFlush(AuditLog.managerCreate(actorId, saved.getId()));
        return AdminManagerCreateResponseDTO.from(saved);
    }
}
