package com.tikit.admin.service;

import com.tikit.admin.dto.request.AdminMemberRoleChangeRequestDTO;
import com.tikit.admin.entity.AuditLog;
import com.tikit.admin.exception.AdminMemberNotFoundException;
import com.tikit.admin.repository.AuditLogRepository;
import com.tikit.auth.entity.*;
import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMemberRoleServiceTest {
    @Mock MemberRepository members;
    @Mock AuditLogRepository audit;
    @Mock RefreshTokenRepository tokens;
    @InjectMocks AdminMemberService service;

    private Member member(Role role, MemberStatus status) {
        Member member = Member.create("member@example.com", "hash", "회원", role);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "version", 3L);
        return member;
    }

    private void admin() {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN, MemberStatus.ACTIVE)));
    }

    private AdminMemberRoleChangeRequestDTO request() {
        return new AdminMemberRoleChangeRequestDTO(Role.MANAGER, 3L, " 공연 운영 담당 ");
    }

    @Test
    void promotesExistingMemberAndRecordsAuditWithLatestVersion() {
        admin();
        Member target = member(Role.MEMBER, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(target, "id", 2L);
        when(members.findById(2L)).thenReturn(Optional.of(target));
        when(audit.saveAndFlush(any())).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(target, "version", 4L);
            return invocation.getArgument(0);
        });
        var response = service.changeRole(1L, 2L, request());
        assertThat(response.role()).isEqualTo(Role.MANAGER);
        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.version()).isEqualTo(4L);
        assertThat(target.getPasswordHash()).isEqualTo("hash");
        ArgumentCaptor<AuditLog> log = ArgumentCaptor.forClass(AuditLog.class);
        verify(audit).saveAndFlush(log.capture());
        assertThat(log.getValue().getActorMemberId()).isEqualTo(1L);
        assertThat(log.getValue().getTargetId()).isEqualTo(2L);
        assertThat(log.getValue().getAction()).isEqualTo("MEMBER_ROLE_CHANGE");
        assertThat(log.getValue().getReason()).isEqualTo("공연 운영 담당");
        assertThat(log.getValue().getBeforeState()).containsEntry("role", "MEMBER");
        assertThat(log.getValue().getAfterState()).containsEntry("role", "MANAGER");
        verify(members, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"MEMBER,ACTIVE", "MANAGER,ACTIVE", "ADMIN,SUSPENDED", "ADMIN,WITHDRAWN"})
    void rejectsUnauthorizedActor(Role role, MemberStatus status) {
        when(members.findById(1L)).thenReturn(Optional.of(member(role, status)));
        assertThatThrownBy(() -> service.changeRole(1L, 2L, request())).isInstanceOf(MemberStatusChangeException.class);
        verify(members, never()).findById(2L);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,ACTIVE", "MANAGER,ACTIVE", "MEMBER,SUSPENDED", "MEMBER,WITHDRAWN"})
    void rejectsProtectedOrInvalidTarget(Role role, MemberStatus status) {
        admin();
        Member target = member(role, status);
        when(members.findById(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.changeRole(1L, 2L, request())).isInstanceOf(MemberStatusChangeException.class);
        assertThat(target.getRole()).isEqualTo(role);
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsSelf() {
        admin();
        assertThatThrownBy(() -> service.changeRole(1L, 1L, request())).isInstanceOf(MemberStatusChangeException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void missingMemberReturnsNotFound() {
        admin();
        assertThatThrownBy(() -> service.changeRole(1L, 2L, request())).isInstanceOf(AdminMemberNotFoundException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsStaleVersionBeforeChangingRole() {
        admin();
        Member target = member(Role.MEMBER, MemberStatus.ACTIVE);
        when(members.findById(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.changeRole(1L, 2L,
                new AdminMemberRoleChangeRequestDTO(Role.MANAGER, 2L, "사유")))
                .isInstanceOfSatisfying(MemberStatusChangeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        assertThat(target.getRole()).isEqualTo(Role.MEMBER);
        verifyNoInteractions(audit);
    }

    @Test
    void propagatesConcurrentVersionConflict() {
        admin();
        when(members.findById(2L)).thenReturn(Optional.of(member(Role.MEMBER, MemberStatus.ACTIVE)));
        when(audit.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(Member.class, 2L));
        assertThatThrownBy(() -> service.changeRole(1L, 2L, request()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void demotesManagerAndRecordsRoleHistory() {
        admin();
        Member target = member(Role.MANAGER, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(target, "id", 2L);
        when(members.findById(2L)).thenReturn(Optional.of(target));
        when(audit.saveAndFlush(any())).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(target, "version", 4L);
            return invocation.getArgument(0);
        });
        var response = service.changeRole(1L, 2L,
                new AdminMemberRoleChangeRequestDTO(Role.MEMBER, 3L, " 운영 담당 해제 "));
        assertThat(response.role()).isEqualTo(Role.MEMBER);
        assertThat(response.version()).isEqualTo(4L);
        assertThat(target.getPasswordHash()).isEqualTo("hash");
        ArgumentCaptor<AuditLog> log = ArgumentCaptor.forClass(AuditLog.class);
        verify(audit).saveAndFlush(log.capture());
        assertThat(log.getValue().getBeforeState()).containsEntry("role", "MANAGER");
        assertThat(log.getValue().getAfterState()).containsEntry("role", "MEMBER");
        assertThat(log.getValue().getReason()).isEqualTo("운영 담당 해제");
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,ACTIVE", "MEMBER,ACTIVE", "MANAGER,SUSPENDED", "MANAGER,WITHDRAWN"})
    void rejectsInvalidDemotion(Role role, MemberStatus status) {
        admin();
        Member target = member(role, status);
        when(members.findById(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.changeRole(1L, 2L,
                new AdminMemberRoleChangeRequestDTO(Role.MEMBER, 3L, "해제")))
                .isInstanceOf(MemberStatusChangeException.class);
        assertThat(target.getRole()).isEqualTo(role);
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsDemotionWithStaleVersion() {
        admin();
        Member target = member(Role.MANAGER, MemberStatus.ACTIVE);
        when(members.findById(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.changeRole(1L, 2L,
                new AdminMemberRoleChangeRequestDTO(Role.MEMBER, 2L, "해제")))
                .isInstanceOfSatisfying(MemberStatusChangeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        assertThat(target.getRole()).isEqualTo(Role.MANAGER);
        verifyNoInteractions(audit);
    }

    @Test
    void cannotAssignAdminRole() {
        admin();
        assertThatThrownBy(() -> service.changeRole(1L, 2L,
                new AdminMemberRoleChangeRequestDTO(Role.ADMIN, 3L, "사유")))
                .isInstanceOfSatisfying(MemberStatusChangeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(audit);
    }
}
