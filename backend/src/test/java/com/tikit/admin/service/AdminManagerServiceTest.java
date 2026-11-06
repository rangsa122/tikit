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
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminManagerServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Spy private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    @InjectMocks private AdminManagerService service;

    private final AdminManagerCreateRequestDTO request = new AdminManagerCreateRequestDTO(
            "  Manager@Example.com  ", "Example-password-2026!", "운영 담당자");

    private Member actor(Role role, MemberStatus status) {
        Member member = Member.create("admin@example.com", "hash", "관리자", role);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private void activeAdmin() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(actor(Role.ADMIN, MemberStatus.ACTIVE)));
    }

    private void saveManager() {
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member manager = invocation.getArgument(0);
            ReflectionTestUtils.setField(manager, "id", 2L);
            ReflectionTestUtils.setField(manager, "version", 0L);
            ReflectionTestUtils.setField(manager, "createdAt", Instant.parse("2026-11-06T00:00:00Z"));
            return manager;
        });
    }

    @Test
    void createsActiveManagerWithEncodedPasswordAndAudit() {
        activeAdmin();
        saveManager();

        AdminManagerCreateResponseDTO response = service.createManager(1L, request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        var order = inOrder(memberRepository, auditLogRepository);
        order.verify(memberRepository).saveAndFlush(memberCaptor.capture());
        order.verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        Member manager = memberCaptor.getValue();
        assertThat(manager.getEmail()).isEqualTo("manager@example.com");
        assertThat(manager.getRole()).isEqualTo(Role.MANAGER);
        assertThat(manager.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(manager.getPasswordHash()).isNotEqualTo(request.getPassword());
        assertThat(passwordEncoder.matches(request.getPassword(), manager.getPasswordHash())).isTrue();
        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.accountStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.createdAt()).isEqualTo(manager.getCreatedAt());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActorMemberId()).isEqualTo(1L);
        assertThat(audit.getTargetId()).isEqualTo(2L);
        assertThat(audit.getAction()).isEqualTo("MANAGER_CREATE");
        assertThat(audit.getBeforeState()).isNull();
        assertThat(audit.getAfterState()).containsOnly(
                java.util.Map.entry("role", "MANAGER"), java.util.Map.entry("status", "ACTIVE"));
        assertThat(audit.getReason()).doesNotContain(request.getPassword(), manager.getPasswordHash());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"MEMBER", "MANAGER"})
    void rejectsChangedRole(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(actor(role, MemberStatus.ACTIVE)));
        assertThatThrownBy(() -> service.createManager(1L, request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(passwordEncoder, auditLogRepository);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void rejectsInactiveAdmin(MemberStatus status) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(actor(Role.ADMIN, status)));
        assertThatThrownBy(() -> service.createManager(1L, request)).isInstanceOf(AccountRestrictedException.class);
        verifyNoInteractions(passwordEncoder, auditLogRepository);
    }

    @Test
    void rejectsMissingActor() {
        assertThatThrownBy(() -> service.createManager(1L, request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(passwordEncoder, auditLogRepository);
    }

    @Test
    void rejectsNormalizedDuplicateEmailBeforeSaving() {
        activeAdmin();
        when(memberRepository.existsByEmail("manager@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createManager(1L, request)).isInstanceOf(DuplicateEmailException.class);
        verifyNoInteractions(passwordEncoder, auditLogRepository);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesConcurrentDuplicateEmail() {
        activeAdmin();
        var constraint = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"),
                "uq_members_email");
        when(memberRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", constraint));
        assertThatThrownBy(() -> service.createManager(1L, request)).isInstanceOf(DuplicateEmailException.class);
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void doesNotMisreportOtherIntegrityFailuresAsDuplicateEmail() {
        activeAdmin();
        var failure = new DataIntegrityViolationException("unrelated constraint");
        when(memberRepository.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.createManager(1L, request)).isSameAs(failure);
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void propagatesAuditFailureForTransactionRollback() {
        activeAdmin();
        saveManager();
        var failure = new DataIntegrityViolationException("audit failed");
        when(auditLogRepository.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.createManager(1L, request)).isSameAs(failure);
    }
}
