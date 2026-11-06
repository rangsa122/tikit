package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.PerformanceSessionListDTO;
import com.tikit.performance.dto.PublicPerformanceDetailDTO;
import com.tikit.performance.dto.request.PerformanceCreateRequestDTO;
import com.tikit.performance.dto.request.PerformanceUpdateRequestDTO;
import com.tikit.performance.dto.request.SalesStatusUpdateRequestDTO;
import com.tikit.performance.dto.response.PerformanceResponseDTO;
import com.tikit.performance.dto.response.PerformanceSessionResponseDTO;
import com.tikit.performance.entity.*;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.GenreRepository;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.performance.repository.SaleOpeningRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PerformanceServiceTest {

    @Mock
    PerformanceRepository performanceRepository;

    @Mock
    GenreRepository genreRepository;

    @Mock
    MemberRepository memberRepository;

    @Mock
    PerformanceSessionRepository performanceSessionRepository;

    @Mock
    SaleOpeningRepository saleOpeningRepository;

    @Mock
    SessionGradePriceRepository sessionGradePriceRepository;

    @Mock
    SessionSeatRepository sessionSeatRepository;

    PerformanceService service;

    private static final Long MEMBER_ID = 1L;

    private static final Long PERFORMANCE_ID = 10L;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        PerformanceAccessChecker accessChecker = new PerformanceAccessChecker(memberRepository);
        service = new PerformanceService(
                performanceRepository,
                genreRepository,
                accessChecker,
                performanceSessionRepository,
                saleOpeningRepository,
                sessionGradePriceRepository,
                sessionSeatRepository
        );
    }

    // =====================================================
    // 테스트 데이터 생성
    // =====================================================

    private Member member(Role role, MemberStatus status) {
        Member member = Member.create("test@test.com", "1234", "김철수", role);
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private Genre genre(){
        return Genre.create("THEATER", "테스트연극", null, 1, true);
    }

    private PerformanceCreateRequestDTO createRequestDTO(PerformanceSource source, String kopisId){
        PerformanceCreateRequestDTO dto = new PerformanceCreateRequestDTO();
        ReflectionTestUtils.setField(dto, "source", source);
        ReflectionTestUtils.setField(dto,"kopisId", kopisId);
        ReflectionTestUtils.setField(dto, "title", "테스트공연");
        ReflectionTestUtils.setField(dto, "genreCode", "THEATER");
        ReflectionTestUtils.setField(dto, "venueName", "테스트극장");
        ReflectionTestUtils.setField(dto, "description", "테스트공연을 소개합니다");
        ReflectionTestUtils.setField(dto, "startDate", LocalDate.of(2026,12,1));
        ReflectionTestUtils.setField(dto, "endDate", LocalDate.of(2026,12,31));
        return dto;
    }

    private Performance draftPerformance(Member manager){
        Performance p = Performance.createManual(manager, genre(),  "제목", "장소", null,
                "소개", null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), null);
        ReflectionTestUtils.setField(p, "version", 0L);
        return p;
    }

    private Performance publicPerformance() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        Performance performance = draftPerformance(manager);
        ReflectionTestUtils.setField(performance, "id", PERFORMANCE_ID);
        ReflectionTestUtils.setField(performance, "publicationStatus", PublicationStatus.PUBLISHED);
        ReflectionTestUtils.setField(performance, "visibility", Visibility.PUBLIC);
        return performance;
    }

    private PerformanceSession session(Performance performance, Long id, Instant startsAt) {
        PerformanceSession session = PerformanceSession.create(
                performance,
                1L,
                1L,
                startsAt,
                startsAt.minusSeconds(3600),
                startsAt.minusSeconds(7200),
                "v1",
                "테스트 취소 정책"
        );
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private PerformanceUpdateRequestDTO updateRequest(Long expectedVersion) {
        PerformanceUpdateRequestDTO dto = new PerformanceUpdateRequestDTO();
        ReflectionTestUtils.setField(dto, "expectedVersion", expectedVersion);
        return dto;
    }

    private void assertFails(Runnable action, ErrorCode expected){
        assertThatThrownBy(action::run)
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    private SalesStatusUpdateRequestDTO salesStatusRequest(SalesStatus status, Long expectedVersion) {
        SalesStatusUpdateRequestDTO dto = new SalesStatusUpdateRequestDTO();
        ReflectionTestUtils.setField(dto, "salesStatus", status);
        ReflectionTestUtils.setField(dto, "expectedVersion", expectedVersion);
        return dto;
    }

    /**
     * 공개 공연 조회 Repository 공통 Mock
     */
    private void mockPublicPerformance(Performance performance) {
        when(performanceRepository.findPublicDetail(
                PERFORMANCE_ID,
                PublicationStatus.PUBLISHED,
                Visibility.PUBLIC
        )).thenReturn(Optional.of(performance));
    }

    /**
     * 판매 오픈 데이터 Mock
     */
    private SaleOpening mockOpening() {
        SaleOpening opening = mock(SaleOpening.class);

        when(opening.getId()).thenReturn(1L);

        lenient().when(opening.getOpensAt()).thenReturn(
                Instant.parse("2026-11-20T05:00:00Z")
        );

        return opening;
    }

    /**
     * 회차별 등급 가격 Mock
     */
    private SessionGradePrice mockPrice(Long sessionId, String grade, long amount) {
        SessionGradePrice price = mock(SessionGradePrice.class);
        SessionGradePriceId priceId = mock(SessionGradePriceId.class);

        lenient().when(price.getId()).thenReturn(priceId);
        lenient().when(priceId.getSessionId()).thenReturn(sessionId);
        lenient().when(priceId.getGrade()).thenReturn(grade);

        when(price.getPrice()).thenReturn(amount);

        return price;
    }

    // =====================================================
    // 기존 관리자 기능 테스트
    // =====================================================

    @Test
    @DisplayName("직접등록성공 : DRAFT/PRIVATE/STOPPED로 저장되고 기본 예매 제한은 4")
    void create_manual_success(){
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(genreRepository.findActive("THEATER")).thenReturn(Optional.of(genre()));

        service.create(MEMBER_ID, createRequestDTO(PerformanceSource.MANUAL, null));

        ArgumentCaptor<Performance> captor = ArgumentCaptor.forClass(Performance.class);
        verify(performanceRepository).save(captor.capture());
        Performance saved = captor.getValue();
        assertThat(saved.getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(saved.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(saved.getSalesStatus()).isEqualTo(SalesStatus.STOPPED);
        assertThat(saved.getBookingLimitPerMember()).isEqualTo((short)4);
        assertThat(saved.getCreatedBy()).isSameAs(manager);
    }

    @Test
    @DisplayName("ADMIN은 공연을 등록 할 수 없다")
    void create_admin_denied(){
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(Role.ADMIN, MemberStatus.ACTIVE)));

        assertFails(()->service.create(MEMBER_ID, createRequestDTO(PerformanceSource.MANUAL, null)),
                ErrorCode.ACCESS_DENIED);
        verify(performanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 등록된 kopis_id는 거절한다")
    void create_kopi_duplicate(){
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(Role.MANAGER, MemberStatus.ACTIVE)));
        when(genreRepository.findActive("THEATER")).thenReturn(Optional.of(genre()));
        when(performanceRepository.existsByKopisId("PF123456")).thenReturn(true);

        assertFails(()->service.create(MEMBER_ID, createRequestDTO(PerformanceSource.KOPIS, "PF123456")),
                ErrorCode.KOPIS_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("게시된 공연은 수정되지 않는다")
    void update_published_locked() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        Performance p = draftPerformance(manager);
        ReflectionTestUtils.setField(p, "publicationStatus", PublicationStatus.PUBLISHED);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findDetail(PERFORMANCE_ID)).thenReturn(Optional.of(p));

        assertFails(() -> service.update(MEMBER_ID, PERFORMANCE_ID, updateRequest(0L)),
                ErrorCode.PERFORMANCE_EDIT_LOCKED);
    }

    @Test
    @DisplayName("expectedVersion이 현재 버전과 다르면 수정할 수 없다")
    void update_versionConflict(){
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findDetail(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(manager)));

        assertFails(() -> service.update(MEMBER_ID, PERFORMANCE_ID, updateRequest(5L)),
                ErrorCode.VERSION_CONFLICT);   // 공연의 version은 0, 요청은 5
    }

    @Test
    @DisplayName("보낸 필드만 수정되고 보내지 않은 필드는 그대로다")
    void update_success() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        Performance p = draftPerformance(manager);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findDetail(PERFORMANCE_ID)).thenReturn(Optional.of(p));

        PerformanceUpdateRequestDTO dto = updateRequest(0L);
        ReflectionTestUtils.setField(dto, "title", "변경된 제목");

        service.update(MEMBER_ID, PERFORMANCE_ID, dto);

        assertThat(p.getTitle()).isEqualTo("변경된 제목");   // 바뀜
        assertThat(p.getVenueName()).isEqualTo("장소");      // 안 보냈으니 그대로
        verify(performanceRepository).flush();
    }

    @Test
    @DisplayName("ADMIN은 공연을 조회할 수 있다")
    void get_admin_allowed() {
        Member admin = member(Role.ADMIN, MemberStatus.ACTIVE);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(admin));
        when(performanceRepository.findDetail(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(admin)));

        PerformanceResponseDTO result = service.get(MEMBER_ID, PERFORMANCE_ID);

        assertThat(result.getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("존재하지 않는 장르로는 등록할 수 없다")
    void create_genreNotFound() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(Role.MANAGER, MemberStatus.ACTIVE)));
        when(genreRepository.findActive("THEATER")).thenReturn(Optional.empty());

        assertFails(() -> service.create(MEMBER_ID, createRequestDTO(PerformanceSource.MANUAL, null)),
                ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("종료일이 시작일보다 빠르면 등록할 수 없다")
    void create_endBeforeStart() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(Role.MANAGER, MemberStatus.ACTIVE)));

        PerformanceCreateRequestDTO dto = createRequestDTO(PerformanceSource.MANUAL, null);
        ReflectionTestUtils.setField(dto, "startDate", LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(dto, "endDate", LocalDate.of(2026, 12, 1));

        assertFails(() -> service.create(MEMBER_ID, dto), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("직접 등록(MANUAL)에는 kopisId를 보낼 수 없다")
    void create_manualWithKopisId() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(Role.MANAGER, MemberStatus.ACTIVE)));

        assertFails(() -> service.create(MEMBER_ID, createRequestDTO(PerformanceSource.MANUAL, "PF123456")),
                ErrorCode.VALIDATION_ERROR);
    }

    // =====================================================
    // 이슈 #85 공개 공연 상세 조회 테스트
    // =====================================================

    @Test
    @DisplayName("공개된 공연은 로그인 없이 상세 정보를 조회할 수 있다")
    void getPublicDetail_success() {
        Performance performance = publicPerformance();

        PerformanceSession first = session(performance, 101L, Instant.parse("2026-12-01T10:00:00Z"));
        SaleOpening opening = mockOpening();
        SessionGradePrice price = mockPrice(101L, "R", 50000L);

        mockPublicPerformance(performance);

        when(performanceSessionRepository.findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of(first));
        when(saleOpeningRepository.findByPerformance_IdOrderByOpensAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of(opening));
        when(sessionGradePriceRepository.findBySessionIdIn(List.of(101L)))
                .thenReturn(List.of(price));

        PublicPerformanceDetailDTO result = service.getPublicDetail(PERFORMANCE_ID);

        assertThat(result.performanceId()).isEqualTo("10");
        assertThat(result.title()).isEqualTo("제목");
        assertThat(result.genreCode()).isEqualTo("THEATER");
        assertThat(result.venueName()).isEqualTo("장소");
        assertThat(result.minPrice()).isEqualTo(50000L);
        assertThat(result.salesStatus()).isEqualTo("STOPPED");
        assertThat(result.bookingLimitPerMember()).isEqualTo(4);
        assertThat(result.cancellationPolicy()).isEqualTo("테스트 취소 정책");
        assertThat(result.saleOpenings()).hasSize(1);
        assertThat(result.saleOpenings().getFirst().openingId()).isEqualTo("1");
        assertThat(result.saleOpenings().getFirst().notified()).isFalse();

        verify(performanceRepository).findPublicDetail(
                PERFORMANCE_ID,
                PublicationStatus.PUBLISHED,
                Visibility.PUBLIC
        );
    }

    @Test
    @DisplayName("비공개 공연은 공개 상세 조회에서 찾을 수 없다")
    void getPublicDetail_privateNotFound() {
        when(performanceRepository.findPublicDetail(
                PERFORMANCE_ID,
                PublicationStatus.PUBLISHED,
                Visibility.PUBLIC
        )).thenReturn(Optional.empty());

        assertFails(() -> service.getPublicDetail(PERFORMANCE_ID), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("공연 가격 데이터가 없으면 상세 조회에 실패한다")
    void getPublicDetail_withoutPrices() {
        Performance performance = publicPerformance();

        mockPublicPerformance(performance);

        when(performanceSessionRepository.findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of());
        when(saleOpeningRepository.findByPerformance_IdOrderByOpensAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of());

        assertFails(() -> service.getPublicDetail(PERFORMANCE_ID), ErrorCode.RESOURCE_NOT_FOUND);
    }

    // =====================================================
    // 이슈 #85 공개 공연 회차 조회 테스트
    // =====================================================

    @Test
    @DisplayName("공개 공연의 회차 목록을 시작 시간순으로 조회한다")
    void getPublicSessions_success() {
        Performance performance = publicPerformance();

        Instant firstStartsAt = Instant.parse("2026-12-01T10:00:00Z");
        Instant secondStartsAt = Instant.parse("2026-12-02T10:00:00Z");

        PerformanceSession first = session(performance, 101L, firstStartsAt);
        PerformanceSession second = session(performance, 102L, secondStartsAt);

        SaleOpening opening = mockOpening();

        SessionGradePrice firstPrice = mockPrice(101L, "R", 50000L);
        SessionGradePrice secondPrice = mockPrice(102L, "S", 30000L);

        mockPublicPerformance(performance);

        when(performanceSessionRepository.findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of(first, second));
        when(saleOpeningRepository.findByPerformance_IdOrderByOpensAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of(opening));
        when(sessionGradePriceRepository.findBySessionIdIn(List.of(101L, 102L)))
                .thenReturn(List.of(firstPrice, secondPrice));

        PerformanceSessionListDTO result = service.getPublicSessions(PERFORMANCE_ID);

        assertThat(result.items()).hasSize(2);

        assertThat(result.items())
                .extracting(PerformanceSessionResponseDTO::sessionId)
                .containsExactly("101", "102");

        assertThat(result.items())
                .extracting(PerformanceSessionResponseDTO::performanceId)
                .containsExactly("10", "10");

        assertThat(result.items())
                .extracting(PerformanceSessionResponseDTO::startsAt)
                .containsExactly(
                        firstStartsAt.atZone(SEOUL).toOffsetDateTime(),
                        secondStartsAt.atZone(SEOUL).toOffsetDateTime()
                );

        assertThat(result.items())
                .extracting(PerformanceSessionResponseDTO::bookingAvailability)
                .containsExactly("STOPPED", "STOPPED");

        assertThat(result.items().getFirst().gradePrices()).hasSize(1);
        assertThat(result.items().getFirst().gradePrices().getFirst().grade()).isEqualTo("R");
        assertThat(result.items().getFirst().gradePrices().getFirst().price()).isEqualTo(50000L);

        assertThat(result.items().get(1).gradePrices()).hasSize(1);
        assertThat(result.items().get(1).gradePrices().getFirst().grade()).isEqualTo("S");
        assertThat(result.items().get(1).gradePrices().getFirst().price()).isEqualTo(30000L);

        assertThat(result.items().getFirst().serverTime()).isNotNull();

        verify(performanceSessionRepository)
                .findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID);
    }

    @Test
    @DisplayName("공개 공연에 회차가 없으면 빈 items 목록을 반환한다")
    void getPublicSessions_empty() {
        Performance performance = publicPerformance();

        mockPublicPerformance(performance);

        when(performanceSessionRepository.findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of());

        PerformanceSessionListDTO result = service.getPublicSessions(PERFORMANCE_ID);

        assertThat(result.items()).isEmpty();

        verify(performanceSessionRepository)
                .findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID);

        verifyNoInteractions(
                saleOpeningRepository,
                sessionGradePriceRepository,
                sessionSeatRepository
        );
    }


    // =====================================================
    // 신규 판매 중단 / 재개
    // =====================================================

    @Test
    @DisplayName("게시되고 공개된 공연은 판매를 재개할 수 있다")
    void salesStatus_resume_success() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        Performance p = publicPerformance();   // PUBLISHED + PUBLIC, 판매는 STOPPED

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.of(p));

        service.changeSalesStatus(MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.ENABLED, 0L));

        assertThat(p.getSalesStatus()).isEqualTo(SalesStatus.ENABLED);
        verify(performanceRepository).flush();
    }

    @Test
    @DisplayName("게시된 공연은 신규 판매를 중단할 수 있다")
    void salesStatus_stop_success() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        Performance p = publicPerformance();
        ReflectionTestUtils.setField(p, "salesStatus", SalesStatus.ENABLED);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.of(p));

        service.changeSalesStatus(MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.STOPPED, 0L));

        assertThat(p.getSalesStatus()).isEqualTo(SalesStatus.STOPPED);
        verify(performanceRepository).flush();
    }

    @Test
    @DisplayName("게시 전 공연은 판매를 재개할 수 없다")
    void salesStatus_resume_draft_denied() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(manager)));

        assertFails(() -> service.changeSalesStatus(
                        MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.ENABLED, 0L)),
                ErrorCode.SALE_RESUME_NOT_ALLOWED);
    }

    @Test
    @DisplayName("게시가 중단된(HIDDEN) 공연은 판매를 재개할 수 없다")
    void salesStatus_resume_hidden_denied() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);
        Performance p = publicPerformance();
        ReflectionTestUtils.setField(p, "visibility", Visibility.HIDDEN);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.of(p));

        assertFails(() -> service.changeSalesStatus(
                        MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.ENABLED, 0L)),
                ErrorCode.SALE_RESUME_NOT_ALLOWED);
    }

    @Test
    @DisplayName("게시 전 공연은 판매를 중단할 수 없다")
    void salesStatus_stop_draft_denied() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(manager)));

        assertFails(() -> service.changeSalesStatus(
                        MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.STOPPED, 0L)),
                ErrorCode.PERFORMANCE_NOT_PUBLISHED);
    }

    @Test
    @DisplayName("expectedVersion이 다르면 판매 상태를 바꿀 수 없다")
    void salesStatus_versionConflict() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID))
                .thenReturn(Optional.of(publicPerformance()));   // version 0

        assertFails(() -> service.changeSalesStatus(
                        MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.STOPPED, 5L)),
                ErrorCode.VERSION_CONFLICT);
    }

    @Test
    @DisplayName("존재하지 않는 공연은 판매 상태를 바꿀 수 없다")
    void salesStatus_notFound() {
        Member manager = member(Role.MANAGER, MemberStatus.ACTIVE);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(manager));
        when(performanceRepository.findForSave(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertFails(() -> service.changeSalesStatus(
                        MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.STOPPED, 0L)),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("ADMIN은 판매 상태를 바꿀 수 없다")
    void salesStatus_admin_denied() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(Role.ADMIN, MemberStatus.ACTIVE)));

        assertFails(() -> service.changeSalesStatus(
                        MEMBER_ID, PERFORMANCE_ID, salesStatusRequest(SalesStatus.STOPPED, 0L)),
                ErrorCode.ACCESS_DENIED);
        verify(performanceRepository, never()).flush();
    }
}