package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.response.BookingStatusResponseDTO;
import com.tikit.performance.entity.Genre;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingStatusServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock PerformanceRepository performanceRepository;
    @Mock PerformanceSessionRepository performanceSessionRepository;
    @Mock SessionSeatRepository sessionSeatRepository;
    @Mock BookingRepository bookingRepository;

    BookingStatusService service;

    private static final Long MEMBER_ID = 1L;
    private static final Long PERFORMANCE_ID = 10L;
    private static final Instant STARTS_AT = Instant.parse("2026-11-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new BookingStatusService(
                new PerformanceAccessChecker(memberRepository),
                performanceRepository,
                performanceSessionRepository,
                sessionSeatRepository,
                bookingRepository
        );
    }

    // ===== 도우미 =====

    private void loginAs(Role role) {
        Member member = Member.create("test@test.com", "1234", "김철수", role);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    private PerformanceSession session(Long id) {
        Member manager = Member.create("m@test.com", "1234", "매니저", Role.MANAGER);
        Performance performance = Performance.createManual(manager,
                Genre.create("theater", "연극", "AAAA", 1, true),
                "제목", "장소", null, "소개", null,
                LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 30), null);

        PerformanceSession session = PerformanceSession.create(
                performance, 1L, 1L, STARTS_AT,
                STARTS_AT.minusSeconds(3600), STARTS_AT.minusSeconds(7200),
                "v1", "테스트 취소 정책");
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private SessionSeatRepository.SeatCount seatCount(Long sessionId, long total, long sold, long held) {
        SessionSeatRepository.SeatCount count = mock(SessionSeatRepository.SeatCount.class);
        when(count.getSessionId()).thenReturn(sessionId);
        when(count.getTotalSeats()).thenReturn(total);
        when(count.getSoldSeats()).thenReturn(sold);
        when(count.getHeldSeats()).thenReturn(held);
        return count;
    }

    private BookingRepository.BookingCount bookingCount(Long sessionId, long booked, long canceled, long revenue) {
        BookingRepository.BookingCount count = mock(BookingRepository.BookingCount.class);
        when(count.getSessionId()).thenReturn(sessionId);
        when(count.getBookingCount()).thenReturn(booked);
        when(count.getCanceledCount()).thenReturn(canceled);
        when(count.getRevenue()).thenReturn(revenue);
        return count;
    }

    private void assertFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("회차별 현황과 공연 전체 합계를 함께 반환한다")
    void getBookingStatus_success() {
        loginAs(Role.MANAGER);
        when(performanceRepository.existsById(PERFORMANCE_ID)).thenReturn(true);
        when(performanceSessionRepository.findByPerformance(PERFORMANCE_ID))
                .thenReturn(List.of(session(100L), session(200L)));

        // 바깥 when()을 시작하기 전에 mock을 전부 만들어둔다
        var seat100 = seatCount(100L, 100, 78, 2);
        var seat200 = seatCount(200L, 100, 59, 2);
        var booking100 = bookingCount(100L, 30, 1, 3_900_000L);
        var booking200 = bookingCount(200L, 22, 2, 2_950_000L);

        when(sessionSeatRepository.countBySessions(List.of(100L, 200L)))
                .thenReturn(List.of(seat100, seat200));
        when(bookingRepository.countBySessions(List.of(100L, 200L)))
                .thenReturn(List.of(booking100, booking200));

        BookingStatusResponseDTO result = service.getBookingStatus(MEMBER_ID, PERFORMANCE_ID);

        assertThat(result.sessions()).hasSize(2);
        assertThat(result.summary().totalSeats()).isEqualTo(200);
        assertThat(result.summary().soldSeats()).isEqualTo(137);
        assertThat(result.summary().heldSeats()).isEqualTo(4);
        assertThat(result.summary().bookingCount()).isEqualTo(52);
        assertThat(result.summary().canceledCount()).isEqualTo(3);
        assertThat(result.summary().revenue()).isEqualTo(6_850_000L);
    }

    @Test
    @DisplayName("회차가 없으면 빈 목록과 0 합계를 반환한다")
    void getBookingStatus_noSessions() {
        loginAs(Role.MANAGER);
        when(performanceRepository.existsById(PERFORMANCE_ID)).thenReturn(true);
        when(performanceSessionRepository.findByPerformance(PERFORMANCE_ID)).thenReturn(List.of());

        BookingStatusResponseDTO result = service.getBookingStatus(MEMBER_ID, PERFORMANCE_ID);

        assertThat(result.sessions()).isEmpty();
        assertThat(result.summary().totalSeats()).isZero();
        assertThat(result.summary().revenue()).isZero();
        verify(sessionSeatRepository, never()).countBySessions(any());
    }

    @Test
    @DisplayName("좌석이나 예매가 아직 없는 회차는 0으로 채운다")
    void getBookingStatus_emptySession() {
        loginAs(Role.MANAGER);
        when(performanceRepository.existsById(PERFORMANCE_ID)).thenReturn(true);
        when(performanceSessionRepository.findByPerformance(PERFORMANCE_ID))
                .thenReturn(List.of(session(100L)));
        when(sessionSeatRepository.countBySessions(List.of(100L))).thenReturn(List.of());
        when(bookingRepository.countBySessions(List.of(100L))).thenReturn(List.of());

        BookingStatusResponseDTO result = service.getBookingStatus(MEMBER_ID, PERFORMANCE_ID);

        assertThat(result.sessions()).hasSize(1);
        assertThat(result.sessions().get(0).totalSeats()).isZero();
        assertThat(result.sessions().get(0).revenue()).isZero();
    }

    @Test
    @DisplayName("ADMIN도 조회할 수 있다")
    void getBookingStatus_admin() {
        loginAs(Role.ADMIN);
        when(performanceRepository.existsById(PERFORMANCE_ID)).thenReturn(true);
        when(performanceSessionRepository.findByPerformance(PERFORMANCE_ID)).thenReturn(List.of());

        assertThat(service.getBookingStatus(MEMBER_ID, PERFORMANCE_ID)).isNotNull();
    }

    @Test
    @DisplayName("없는 공연은 조회할 수 없다")
    void getBookingStatus_notFound() {
        loginAs(Role.MANAGER);
        when(performanceRepository.existsById(PERFORMANCE_ID)).thenReturn(false);

        assertFails(() -> service.getBookingStatus(MEMBER_ID, PERFORMANCE_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
        verify(performanceSessionRepository, never()).findByPerformance(anyLong());
    }
}