package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.common.idempotency.IdempotencyService;
import com.tikit.performance.dto.response.PerformancePublicationResponseDTO;
import com.tikit.performance.entity.*;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PerformancePublishServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    PerformanceRepository performanceRepository;

    @Mock
    PerformanceSessionRepository performanceSessionRepository;

    @Mock
    SessionGradePriceRepository sessionGradePriceRepository;

    @Mock
    IdempotencyService idempotencyService;

    PerformancePublishService service;

    private static final Long MEMBER_ID = 1L;
    private static final Long PERFORMANCE_ID = 10L;
    private static final Long SESSION_ID = 100L;
    private static final Long REQUEST_ID = 999L;
    private static final Instant NOW = Instant.parse("2026-11-07T05:00:00Z");

    @BeforeEach
    void setUp() {
        service = new PerformancePublishService(
                new PerformanceAccessChecker(memberRepository),
                performanceRepository,
                performanceSessionRepository,
                sessionGradePriceRepository,
                idempotencyService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    // ===== 도우미 =====

    private Member member(Role role) {
        Member member = Member.create("test@test.com", "1234", "김철수", role);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        return member;
    }

    private Performance draftPerformance(Member manager) {
        Performance performance = Performance.createManual(manager,
                Genre.create("theater", "연극", "AAAA", 1, true),
                "제목", "장소", null, "소개", null,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), null);
        ReflectionTestUtils.setField(performance, "id", PERFORMANCE_ID);
        ReflectionTestUtils.setField(performance, "version", 0L);
        return performance;
    }

    private PerformanceSession session(Performance performance, Long id) {
        Instant startsAt = NOW.plusSeconds(86400);
        PerformanceSession session = PerformanceSession.create(
                performance, 1L, 1L, startsAt,
                startsAt.minusSeconds(3600), startsAt.minusSeconds(7200),
                "v1", "테스트 취소 정책");
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private SessionGradePrice price(PerformanceSession session) {
        return SessionGradePrice.create(session, "VIP", 50000L);
    }

    private void assertFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("회차와 가격이 있는 DRAFT 공연을 게시할 수 있다")
    void publish_success() {
        Member manager = member(Role.MANAGER);
        Performance performance = draftPerformance(manager);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.of(performance));
        PerformanceSession session = session(performance, SESSION_ID);
        when(performanceSessionRepository.findByPerformance(PERFORMANCE_ID))
                .thenReturn(List.of(session));
        when(sessionGradePriceRepository.findBySessions(List.of(SESSION_ID)))
                .thenReturn(List.of(price(session)));

        PerformancePublicationResponseDTO result =
                service.publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID);

        assertThat(performance.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(performance.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(performance.getSalesStatus()).isEqualTo(SalesStatus.ENABLED);
        assertThat(performance.getPublishedAt()).isEqualTo(NOW);
        assertThat(performance.getPublishedBy()).isEqualTo(manager);
        assertThat(result.publicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);

        verify(idempotencyService).succeed(REQUEST_ID, "PERFORMANCE", PERFORMANCE_ID);
        verify(performanceRepository).flush();
    }

    @Test
    @DisplayName("회차가 없으면 게시할 수 없다")
    void publish_noSession() {
        Member manager = member(Role.MANAGER);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(manager)));
        when(performanceSessionRepository.findByPerformance(PERFORMANCE_ID))
                .thenReturn(List.of());

        assertFails(() -> service.publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID),
                ErrorCode.PUBLISH_REQUIREMENTS_MISSING);
        verify(idempotencyService, never()).succeed(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("가격이 없는 회차가 있으면 게시할 수 없다")
    void publish_sessionWithoutPrice() {
        Member manager = member(Role.MANAGER);
        Performance performance = draftPerformance(manager);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.of(performance));
        PerformanceSession withPrice = session(performance, SESSION_ID);
        PerformanceSession withoutPrice = session(performance, 200L);
        when(performanceSessionRepository.findByPerformance(PERFORMANCE_ID))
                .thenReturn(List.of(withPrice, withoutPrice));
        // 100번 회차에만 가격이 있다
        when(sessionGradePriceRepository.findBySessions(List.of(SESSION_ID, 200L)))
                .thenReturn(List.of(price(withPrice)));

        assertFails(() -> service.publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID),
                ErrorCode.PUBLISH_REQUIREMENTS_MISSING);
    }

    @Test
    @DisplayName("이미 게시된 공연은 다시 게시할 수 없다")
    void publish_alreadyPublished() {
        Member manager = member(Role.MANAGER);
        Performance performance = draftPerformance(manager);
        ReflectionTestUtils.setField(performance, "publicationStatus", PublicationStatus.PUBLISHED);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.of(performance));

        assertFails(() -> service.publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID),
                ErrorCode.PERFORMANCE_ALREADY_PUBLISHED);
    }

    @Test
    @DisplayName("expectedVersion이 다르면 게시할 수 없다")
    void publish_versionConflict() {
        Member manager = member(Role.MANAGER);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(manager)));   // version 0

        assertFails(() -> service.publish(MEMBER_ID, PERFORMANCE_ID, 5L, REQUEST_ID),
                ErrorCode.VERSION_CONFLICT);
    }

    @Test
    @DisplayName("존재하지 않는 공연은 게시할 수 없다")
    void publish_notFound() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(Role.MANAGER)));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertFails(() -> service.publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("ADMIN은 공연을 게시할 수 없다")
    void publish_admin_denied() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(Role.ADMIN)));

        assertFails(() -> service.publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID),
                ErrorCode.ACCESS_DENIED);
        verify(performanceRepository, never()).findForSave(anyLong());
    }
}