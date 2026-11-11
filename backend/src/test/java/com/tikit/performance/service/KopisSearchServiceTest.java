package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.response.KopisPerformanceDetailResponseDTO;
import com.tikit.performance.dto.response.KopisPerformanceSearchResponseDTO;
import com.tikit.performance.entity.Genre;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.kopis.KopisClient;
import com.tikit.performance.kopis.KopisPerformanceDetail;
import com.tikit.performance.kopis.KopisPerformanceSummary;
import com.tikit.performance.repository.GenreRepository;
import com.tikit.performance.repository.PerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KopisSearchServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    GenreRepository genreRepository;

    @Mock
    PerformanceRepository performanceRepository;

    @Mock
    KopisClient kopisClient;

    KopisSearchService service;

    private static final Long MEMBER_ID = 1L;
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);

    @BeforeEach
    void setUp() {
        service = new KopisSearchService(
                new PerformanceAccessChecker(memberRepository),
                genreRepository,
                performanceRepository,
                kopisClient
        );
    }

    // ===== 도우미 =====

    private void loginAs(Role role) {
        Member member = Member.create("test@test.com", "1234", "김철수", role);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    private KopisPerformanceSummary summary(String kopisId) {
        return new KopisPerformanceSummary(
                kopisId, "테스트공연",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                "테스트극장", "http://poster.png", "서울특별시", "연극", "공연예정");
    }

    private KopisPerformanceDetail detail(String genreName, String story) {
        return new KopisPerformanceDetail(
                "PF001", "테스트공연",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                "테스트극장", "http://poster.png", genreName, story,
                "테스트기획사", "홍길동", "120분", "만 7세 이상",
                "화요일 ~ 금요일(19:30)", "R석 50,000원", "공연예정");
    }

    private Genre theater() {
        return Genre.create("theater", "연극", "AAAA", 1, true);
    }

    private void assertFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("이미 등록된 공연은 alreadyRegistered가 true다")
    void search_marksRegistered() {
        loginAs(Role.MANAGER);
        when(kopisClient.searchPerformances(START, END, null, null, 1, 20))
                .thenReturn(List.of(summary("PF000001"), summary("PF000002")));
        when(performanceRepository.findRegisteredKopisIds(List.of("PF000001", "PF000002")))
                .thenReturn(List.of("PF000002"));

        KopisPerformanceSearchResponseDTO result =
                service.search(MEMBER_ID, START, END, null, null, 0, 20);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).alreadyRegistered()).isFalse();
        assertThat(result.items().get(1).alreadyRegistered()).isTrue();
    }

    @Test
    @DisplayName("우리 장르 코드를 KOPIS 장르 코드로 바꿔 호출한다")
    void search_convertsGenreCode() {
        loginAs(Role.MANAGER);
        when(genreRepository.findActive("MUSICAL"))
                .thenReturn(Optional.of(Genre.create("MUSICAL", "뮤지컬", "GGGA", 9, true)));
        when(kopisClient.searchPerformances(START, END, null, "GGGA", 1, 20))
                .thenReturn(List.of());

        service.search(MEMBER_ID, START, END, null, "MUSICAL", 0, 20);

        verify(kopisClient).searchPerformances(START, END, null, "GGGA", 1, 20);
    }

    @Test
    @DisplayName("없는 장르 코드로는 검색할 수 없다")
    void search_unknownGenre() {
        loginAs(Role.MANAGER);
        when(genreRepository.findActive("NOPE")).thenReturn(Optional.empty());

        assertFails(() -> service.search(MEMBER_ID, START, END, null, "NOPE", 0, 20),
                ErrorCode.VALIDATION_ERROR);
        verify(kopisClient, never())
                .searchPerformances(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("조회 기간이 31일을 넘으면 거절한다")
    void search_rangeTooLong() {
        loginAs(Role.MANAGER);

        assertFails(() -> service.search(
                        MEMBER_ID, START, LocalDate.of(2026, 12, 10), null, null, 0, 20),
                ErrorCode.VALIDATION_ERROR);
        verify(kopisClient, never())
                .searchPerformances(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞서면 거절한다")
    void search_endBeforeStart() {
        loginAs(Role.MANAGER);

        assertFails(() -> service.search(
                        MEMBER_ID, END, START, null, null, 0, 20),
                ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("size가 100을 넘으면 100으로 줄인다")
    void search_limitsSize() {
        loginAs(Role.MANAGER);
        when(kopisClient.searchPerformances(START, END, null, null, 1, 100))
                .thenReturn(List.of());

        service.search(MEMBER_ID, START, END, null, null, 0, 500);

        verify(kopisClient).searchPerformances(START, END, null, null, 1, 100);
    }

    @Test
    @DisplayName("page 0은 KOPIS의 cpage 1로 바뀐다")
    void search_convertsPage() {
        loginAs(Role.MANAGER);
        when(kopisClient.searchPerformances(START, END, null, null, 3, 20))
                .thenReturn(List.of());

        service.search(MEMBER_ID, START, END, null, null, 2, 20);

        verify(kopisClient).searchPerformances(START, END, null, null, 3, 20);
    }

    @Test
    @DisplayName("결과가 없으면 DB를 조회하지 않는다")
    void search_emptyResult() {
        loginAs(Role.MANAGER);
        when(kopisClient.searchPerformances(START, END, null, null, 1, 20))
                .thenReturn(List.of());

        KopisPerformanceSearchResponseDTO result =
                service.search(MEMBER_ID, START, END, null, null, 0, 20);

        assertThat(result.items()).isEmpty();
        verify(performanceRepository, never()).findRegisteredKopisIds(any());
    }

    @Test
    @DisplayName("일반 회원은 KOPIS를 검색할 수 없다")
    void search_member_denied() {
        loginAs(Role.MEMBER);

        assertFails(() -> service.search(MEMBER_ID, START, END, null, null, 0, 20),
                ErrorCode.ACCESS_DENIED);
        verify(kopisClient, never())
                .searchPerformances(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("KOPIS 상세를 조회해 등록 폼에 쓸 값을 돌려준다")
    void getDetail_success() {
        loginAs(Role.MANAGER);
        when(kopisClient.getPerformance("PF001")).thenReturn(detail("연극", "줄거리입니다"));
        when(genreRepository.findActive("theater")).thenReturn(Optional.of(theater()));
        when(performanceRepository.existsByKopisId("PF001")).thenReturn(false);

        KopisPerformanceDetailResponseDTO result = service.getDetail(MEMBER_ID, "PF001");

        assertThat(result.genreCode()).isEqualTo("theater");
        assertThat(result.genreName()).isEqualTo("연극");
        assertThat(result.description()).isEqualTo("줄거리입니다");
        assertThat(result.posterUrl()).isEqualTo("https://poster.png");
        assertThat(result.alreadyRegistered()).isFalse();
    }

    @Test
    @DisplayName("매핑에 없는 장르명이면 genreCode만 null이고 조회는 성공한다")
    void getDetail_unknownGenre() {
        loginAs(Role.MANAGER);
        when(kopisClient.getPerformance("PF001")).thenReturn(detail("서커스", "줄거리입니다"));
        when(performanceRepository.existsByKopisId("PF001")).thenReturn(false);

        KopisPerformanceDetailResponseDTO result = service.getDetail(MEMBER_ID, "PF001");

        assertThat(result.genreCode()).isNull();
        assertThat(result.genreName()).isEqualTo("서커스");
    }

    @Test
    @DisplayName("줄거리가 비어 있으면 다른 정보로 설명을 만든다")
    void getDetail_emptyStory() {
        loginAs(Role.MANAGER);
        when(kopisClient.getPerformance("PF001")).thenReturn(detail("연극", ""));
        when(genreRepository.findActive("theater")).thenReturn(Optional.of(theater()));
        when(performanceRepository.existsByKopisId("PF001")).thenReturn(false);

        KopisPerformanceDetailResponseDTO result = service.getDetail(MEMBER_ID, "PF001");

        assertThat(result.description())
                .contains("공연장: 테스트극장")
                .contains("출연: 홍길동")
                .contains("관람시간: 120분");
    }

    @Test
    @DisplayName("이미 등록된 KOPIS 공연이면 alreadyRegistered가 true다")
    void getDetail_alreadyRegistered() {
        loginAs(Role.MANAGER);
        when(kopisClient.getPerformance("PF001")).thenReturn(detail("연극", "줄거리입니다"));
        when(genreRepository.findActive("theater")).thenReturn(Optional.of(theater()));
        when(performanceRepository.existsByKopisId("PF001")).thenReturn(true);

        assertThat(service.getDetail(MEMBER_ID, "PF001").alreadyRegistered()).isTrue();
    }

    @Test
    @DisplayName("kopisId가 비어 있으면 KOPIS를 호출하지 않고 거절한다")
    void getDetail_blankId() {
        loginAs(Role.MANAGER);

        assertFails(() -> service.getDetail(MEMBER_ID, "  "), ErrorCode.VALIDATION_ERROR);
        verify(kopisClient, never()).getPerformance(any());
    }
}