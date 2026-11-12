package com.tikit.admin.service;

import com.tikit.admin.entity.AuditLog;
import com.tikit.admin.exception.AdminMemberNotFoundException;
import com.tikit.admin.repository.AuditLogRepository;
import com.tikit.auth.entity.*;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMemberDetailServiceTest {
    @Mock MemberRepository members;
    @Mock AuditLogRepository audit;
    @Mock RefreshTokenRepository tokens;
    @InjectMocks AdminMemberService service;

    private final Instant suspendedAt = Instant.parse("2026-11-06T01:00:00Z");

    private Member member(MemberStatus status) {
        Member member = Member.create("member@example.com", "hash", "회원", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 2L);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private void history(AuditLog log) {
        when(audit.findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
                "MEMBER", 2L, List.of("MEMBER_SUSPEND", "MEMBER_RESUME"))).thenReturn(Optional.ofNullable(log));
    }

    private AuditLog suspension(String reason) {
        AuditLog log = AuditLog.memberStatusChange(1L, 2L, reason, MemberStatus.ACTIVE, MemberStatus.SUSPENDED);
        ReflectionTestUtils.setField(log, "createdAt", suspendedAt);
        return log;
    }

    @Test
    void returnsCurrentSuspensionReasonTimeAndActor() {
        when(members.findById(2L)).thenReturn(Optional.of(member(MemberStatus.SUSPENDED)));
        history(suspension("  운영 정책 위반  "));
        var result = service.getMember(2L);
        assertThat(result.suspensionReason()).isEqualTo("운영 정책 위반");
        assertThat(result.suspendedAt()).isEqualTo(suspendedAt);
        assertThat(result.suspendedBy()).isEqualTo(1L);
        assertThat(result.accountStatus()).isEqualTo(MemberStatus.SUSPENDED);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"ACTIVE", "WITHDRAWN"})
    void doesNotExposePastSuspensionForNonSuspendedMember(MemberStatus status) {
        when(members.findById(2L)).thenReturn(Optional.of(member(status)));
        var result = service.getMember(2L);
        assertThat(result.suspensionReason()).isNull();
        assertThat(result.suspendedAt()).isNull();
        assertThat(result.suspendedBy()).isNull();
        verifyNoInteractions(audit);
    }

    @Test
    void legacySuspensionWithoutAuditReturnsNullMetadata() {
        when(members.findById(2L)).thenReturn(Optional.of(member(MemberStatus.SUSPENDED)));
        history(null);
        var result = service.getMember(2L);
        assertThat(result.suspensionReason()).isNull();
        assertThat(result.suspendedAt()).isNull();
        assertThat(result.suspendedBy()).isNull();
    }

    @Test
    void latestResumeCannotBeDisplayedAsSuspensionReason() {
        when(members.findById(2L)).thenReturn(Optional.of(member(MemberStatus.SUSPENDED)));
        history(AuditLog.memberStatusChange(1L, 2L, "정지 해제 승인", MemberStatus.SUSPENDED, MemberStatus.ACTIVE));
        var result = service.getMember(2L);
        assertThat(result.suspensionReason()).isNull();
        assertThat(result.suspendedAt()).isNull();
        assertThat(result.suspendedBy()).isNull();
    }

    @Test
    void resuspendedMemberReceivesLatestReason() {
        when(members.findById(2L)).thenReturn(Optional.of(member(MemberStatus.SUSPENDED)));
        history(suspension("첫 번째 정지"));
        assertThat(service.getMember(2L).suspensionReason()).isEqualTo("첫 번째 정지");
        history(suspension("재정지 사유"));
        assertThat(service.getMember(2L).suspensionReason()).isEqualTo("재정지 사유");
    }

    @Test
    void missingMemberDoesNotQueryAudit() {
        assertThatThrownBy(() -> service.getMember(2L)).isInstanceOf(AdminMemberNotFoundException.class);
        verifyNoInteractions(audit);
    }
}
