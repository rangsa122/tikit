package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.SeatTemplateListResponseDTO;
import com.tikit.performance.dto.SessionConfigurationDTO;
import com.tikit.performance.dto.request.SalesConfigurationSaveRequestDTO;
import com.tikit.performance.dto.response.SalesConfigurationResponseDTO;
import com.tikit.performance.entity.*;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.performance.repository.SaleOpeningRepository;
import com.tikit.performance.repository.SeatTemplateRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import com.tikit.seat.repository.TemplateSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SalesConfigurationServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    SeatTemplateRepository seatTemplateRepository;
    @Mock
    TemplateSeatRepository templateSeatRepository;
    @Mock
    PerformanceRepository performanceRepository;
    @Mock
    PerformanceSessionRepository performanceSessionRepository;
    @Mock
    SaleOpeningRepository saleOpeningRepository;
    @Mock
    SessionGradePriceRepository sessionGradePriceRepository;

    @Mock
    SessionSeatRepository sessionSeatRepository;

    SalesConfigurationService service;

    @BeforeEach
    void setUp() {
        PerformanceAccessChecker checker = new PerformanceAccessChecker(memberRepository);
        service = new SalesConfigurationService(checker, seatTemplateRepository, templateSeatRepository, performanceRepository,
                performanceSessionRepository, saleOpeningRepository, sessionGradePriceRepository, sessionSeatRepository);
    }

    private Member member(Role role) {
        Member m = Member.create("test@test.com", "1234", "김철수", role);
        ReflectionTestUtils.setField(m, "id", 1L);
        ReflectionTestUtils.setField(m, "status", MemberStatus.ACTIVE);
        return m;
    }

    private SeatTemplate template(Long id, String name) {
        SeatTemplate t = SeatTemplate.create(name, 1, true);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private TemplateSeat seat(SeatTemplate t, String code, String row, int number, String grade) {
        TemplateSeat s = BeanUtils.instantiateClass(TemplateSeat.class);
        ReflectionTestUtils.setField(s, "template", t);
        ReflectionTestUtils.setField(s, "seatCode", code);
        ReflectionTestUtils.setField(s, "label", code);
        ReflectionTestUtils.setField(s, "rowLabel", row);
        ReflectionTestUtils.setField(s, "seatNumber", number);
        ReflectionTestUtils.setField(s, "grade", grade);
        ReflectionTestUtils.setField(s, "x", 0);
        ReflectionTestUtils.setField(s, "y", 0);
        return s;
    }

    private Performance performance(Member manager) {
        Genre genre = Genre.create("THEATER", "테스트연극", null, 1, true);
        Performance p = Performance.createManual(manager, genre, "제목", "장소", null, "소개", null,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), null);
        ReflectionTestUtils.setField(p, "id", 10L);
        ReflectionTestUtils.setField(p, "version", 3L);
        return p;
    }

    private PerformanceSession session(Performance p, Long id, Long openingId, Instant startsAt) {
        PerformanceSession s = PerformanceSession.create(p, openingId, 1L, startsAt,
                startsAt.minusSeconds(3600), startsAt.minusSeconds(86400), "v1", "취소 규정");
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private SaleOpening opening(Performance p, Long id, Instant opensAt) {
        SaleOpening o = SaleOpening.create(p, opensAt);
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }

    private SalesConfigurationSaveRequestDTO saveRequest(Long expectedVersion) {
        return new SalesConfigurationSaveRequestDTO(expectedVersion, 4, List.of());
    }

    private void assertFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    @DisplayName("템플릿별로 좌석이 묶이고 행·번호 순으로 정렬된다")
    void list_groupsSeatsByTemplate() {
        SeatTemplate t1 = template(1L, "소극장 100석");
        SeatTemplate t2 = template(2L, "중극장 300석");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.MANAGER)));
        when(seatTemplateRepository.findActive()).thenReturn(List.of(t1, t2));
        when(templateSeatRepository.findByTemplateIdIn(List.of(1L, 2L))).thenReturn(List.of(
                seat(t1, "A-02", "A", 2, "R"),
                seat(t2, "B-01", "B", 1, "S"),
                seat(t1, "A-01", "A", 1, "R")));

        SeatTemplateListResponseDTO result = service.listSeatTemplate(1L);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).seats()).extracting("seatCode")
                .containsExactly("A-01", "A-02");     // 번호순 정렬
        assertThat(result.items().get(1).seats()).hasSize(1);
    }

    @Test
    @DisplayName("활성 템플릿이 없으면 빈 목록을 돌려주고 좌석은 조회하지 않는다")
    void list_empty() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN)));
        when(seatTemplateRepository.findActive()).thenReturn(List.of());

        assertThat(service.listSeatTemplate(1L).items()).isEmpty();
        verify(templateSeatRepository, never()).findByTemplateIdIn(any());
    }

    @Test
    @DisplayName("일반 회원은 템플릿 목록을 조회할 수 없다")
    void list_memberDenied() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.MEMBER)));

        assertThatThrownBy(() -> service.listSeatTemplate(1L))
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("회차의 판매 시작 시각과 등급별 가격이 함께 조회된다")
    void get_success() {
        Member manager = member(Role.MANAGER);
        Performance performance = performance(manager);
        Instant startsAt = Instant.parse("2026-12-10T05:00:00Z");              // 한국 시간 14:00
        PerformanceSession session = session(performance, 100L, 5L, startsAt);
        SaleOpening opening = opening(performance, 5L, Instant.parse("2026-11-20T01:00:00Z"));
        SessionGradePrice r = SessionGradePrice.create(session, "R", 50000L);  // session의 id를 먼저 넣은 뒤에 만들어야 해요
        SessionGradePrice s = SessionGradePrice.create(session, "S", 30000L);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(performanceRepository.findById(10L)).thenReturn(Optional.of(performance));
        when(performanceSessionRepository.findByPerformance(10L)).thenReturn(List.of(session));
        when(saleOpeningRepository.findAllById(List.of(5L))).thenReturn(List.of(opening));
        when(sessionGradePriceRepository.findBySessions(List.of(100L))).thenReturn(List.of(s, r));

        SalesConfigurationResponseDTO result = service.getSalesConfiguration(1L, 10L);

        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.bookingLimitPerMember()).isEqualTo(4);               // 기본값
        SessionConfigurationDTO dto = result.sessions().get(0);
        assertThat(dto.sessionId()).isEqualTo(100L);
        assertThat(dto.startsAt()).isEqualTo(OffsetDateTime.parse("2026-12-10T14:00:00+09:00"));
        assertThat(dto.salesOpenAt()).isEqualTo(OffsetDateTime.parse("2026-11-20T10:00:00+09:00"));
        assertThat(dto.gradePrices()).extracting("grade").containsExactly("R", "S");   // 등급 순 정렬
    }

    @Test
    @DisplayName("회차가 없는 공연은 빈 회차 목록을 돌려준다")
    void get_noSessions() {
        Member manager = member(Role.MANAGER);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(performanceRepository.findById(10L)).thenReturn(Optional.of(performance(manager)));
        when(performanceSessionRepository.findByPerformance(10L)).thenReturn(List.of());

        assertThat(service.getSalesConfiguration(1L, 10L).sessions()).isEmpty();
        verify(saleOpeningRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("없는 공연이면 RESOURCE_NOT_FOUND")
    void get_performanceNotFound() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.MANAGER)));
        when(performanceRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSalesConfiguration(1L, 10L))
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("일반 회원은 판매 설정을 조회할 수 없다")
    void get_memberDenied() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.MEMBER)));

        assertThatThrownBy(() -> service.getSalesConfiguration(1L, 10L))
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("ADMIN은 판매설정을 저장할 수 없다")
    void save_adminDenied(){
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN)));

        assertFails(()-> service.saveSalesConfiguration(1L, 10L, saveRequest(3L)),
                ErrorCode.ACCESS_DENIED);
        verify(performanceRepository, never()).findForSave(any());
    }

    @Test
    @DisplayName("없는 공연이면 RESOURCE_NOT_FOUND")
    void save_performanceNotFound(){
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.MANAGER)));
        when(performanceRepository.findForSave(10L)).thenReturn(Optional.empty());

        assertFails(()->service.saveSalesConfiguration(1L, 10L, saveRequest(3L)),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("게시된 공연은 판매 설정을 저장 할 수 없다")
    void save_publishedLocked(){
        Member manager = member(Role.MANAGER);
        Performance performance = performance(manager);
        ReflectionTestUtils.setField(performance, "publicationStatus", PublicationStatus.PUBLISHED);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(10L)).thenReturn(Optional.of(performance));

        assertFails(()-> service.saveSalesConfiguration(1L, 10L, saveRequest(3L)),
                ErrorCode.PERFORMANCE_EDIT_LOCKED);
        verify(performanceSessionRepository, never()).findByPerformance(any());
    }

    @Test
    @DisplayName("expectedVersion이 공연버전과 다르면 VERSION_CONFLICT")
    void save_versionConflict(){
        Member manager = member(Role.MANAGER);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(10L)).thenReturn(Optional.of(performance(manager)));

        assertFails(() -> service.saveSalesConfiguration(1L, 10L, saveRequest(5L)),
                ErrorCode.VERSION_CONFLICT);
        verify(performanceSessionRepository, never()).findByPerformance(any());
    }

    @Test
    @DisplayName("선점·예매된 좌석이 있으면 저장할 수 없다")
    void save_takenSeat_locked() {
        Member manager = member(Role.MANAGER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(manager));          // ① 권한
        Performance performance = performance(manager);                                  // DRAFT, id 10, version 3
        given(performanceRepository.findForSave(10L)).willReturn(Optional.of(performance)); // ②
        given(performanceSessionRepository.findByPerformance(10L)).willReturn(List.of(session(performance, 100L, 1L, Instant.parse("2026-12-10T05:00:00Z"))));
        given(sessionSeatRepository.existsTaken(any())).willReturn(true);

        assertFails(
                () -> service.saveSalesConfiguration(1L, 10L, saveRequest(3L)),
                ErrorCode.PERFORMANCE_EDIT_LOCKED);
    }
}
