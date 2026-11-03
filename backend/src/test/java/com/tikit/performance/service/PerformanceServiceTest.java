
package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.PerformanceCreateRequestDTO;
import com.tikit.performance.dto.PerformanceResponseDTO;
import com.tikit.performance.dto.PerformanceSessionResponseDTO;
import com.tikit.performance.dto.PerformanceUpdateRequestDTO;
import com.tikit.performance.entity.*;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.GenreRepository;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
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

    @InjectMocks
    PerformanceService service;

    private static final Long MEMBER_ID = 1L;
    private static final Long PERFORMANCE_ID = 10L;

    private Member member(Role role, MemberStatus status) {
        Member member = Member.create(
                "test@test.com",
                "1234",
                "김철수",
                role
        );
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private Genre genre() {
        return Genre.create(
                "THEATER",
                "테스트연극",
                null,
                1,
                true
        );
    }

    private PerformanceCreateRequestDTO createRequestDTO(
            PerformanceSource source,
            String kopisId
    ) {
        PerformanceCreateRequestDTO dto =
                new PerformanceCreateRequestDTO();

        ReflectionTestUtils.setField(dto, "source", source);
        ReflectionTestUtils.setField(dto, "kopisId", kopisId);
        ReflectionTestUtils.setField(dto, "title", "테스트공연");
        ReflectionTestUtils.setField(dto, "genreCode", "THEATER");
        ReflectionTestUtils.setField(dto, "venueName", "테스트극장");
        ReflectionTestUtils.setField(
                dto,
                "description",
                "테스트공연을 소개합니다"
        );
        ReflectionTestUtils.setField(
                dto,
                "startDate",
                LocalDate.of(2026, 12, 1)
        );
        ReflectionTestUtils.setField(
                dto,
                "endDate",
                LocalDate.of(2026, 12, 31)
        );

        return dto;
    }

    private Performance draftPerformance(Member manager) {
        Performance p = Performance.createManual(
                manager,
                genre(),
                "제목",
                "장소",
                null,
                "소개",
                null,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31),
                null
        );

        ReflectionTestUtils.setField(p, "version", 0L);
        return p;
    }

    private Performance publicPerformance() {
        Member manager = member(
                Role.MANAGER,
                MemberStatus.ACTIVE
        );

        Performance performance = draftPerformance(manager);

        ReflectionTestUtils.setField(
                performance,
                "id",
                PERFORMANCE_ID
        );
        ReflectionTestUtils.setField(
                performance,
                "publicationStatus",
                PublicationStatus.PUBLISHED
        );
        ReflectionTestUtils.setField(
                performance,
                "visibility",
                Visibility.PUBLIC
        );

        return performance;
    }

    private PerformanceSession session(
            Performance performance,
            Long id,
            Instant startsAt
    ) {
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

    private PerformanceUpdateRequestDTO updateRequest(
            Long expectedVersion
    ) {
        PerformanceUpdateRequestDTO dto =
                new PerformanceUpdateRequestDTO();

        ReflectionTestUtils.setField(
                dto,
                "expectedVersion",
                expectedVersion
        );

        return dto;
    }

    private void assertFails(
            Runnable action,
            ErrorCode expected
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // 기존 테스트

    @Test
    @DisplayName("직접등록성공 : DRAFT/PRIVATE/STOPPED로 저장되고 기본 예매 제한은 4")
    void create_manual_success() {
        Member manager = member(
                Role.MANAGER,
                MemberStatus.ACTIVE
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(manager));
        when(genreRepository.findActive("THEATER"))
                .thenReturn(Optional.of(genre()));

        service.create(
                MEMBER_ID,
                createRequestDTO(PerformanceSource.MANUAL, null)
        );

        ArgumentCaptor<Performance> captor =
                ArgumentCaptor.forClass(Performance.class);

        verify(performanceRepository).save(captor.capture());

        Performance saved = captor.getValue();

        assertThat(saved.getPublicationStatus())
                .isEqualTo(PublicationStatus.DRAFT);
        assertThat(saved.getVisibility())
                .isEqualTo(Visibility.PRIVATE);
        assertThat(saved.getSalesStatus())
                .isEqualTo(SalesStatus.STOPPED);
        assertThat(saved.getBookingLimitPerMember())
                .isEqualTo((short) 4);
        assertThat(saved.getCreatedBy())
                .isSameAs(manager);
    }

    @Test
    @DisplayName("ADMIN은 공연을 등록 할 수 없다")
    void create_admin_denied() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(Role.ADMIN, MemberStatus.ACTIVE)
                ));

        assertFails(
                () -> service.create(
                        MEMBER_ID,
                        createRequestDTO(
                                PerformanceSource.MANUAL,
                                null
                        )
                ),
                ErrorCode.ACCESS_DENIED
        );

        verify(performanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 등록된 kopis_id는 거절한다")
    void create_kopi_duplicate() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(Role.MANAGER, MemberStatus.ACTIVE)
                ));
        when(genreRepository.findActive("THEATER"))
                .thenReturn(Optional.of(genre()));
        when(performanceRepository.existsByKopisId("PF123456"))
                .thenReturn(true);

        assertFails(
                () -> service.create(
                        MEMBER_ID,
                        createRequestDTO(
                                PerformanceSource.KOPIS,
                                "PF123456"
                        )
                ),
                ErrorCode.KOPIS_ALREADY_REGISTERED
        );
    }

    @Test
    @DisplayName("게시된 공연은 수정되지 않는다")
    void update_published_locked() {
        Member manager = member(
                Role.MANAGER,
                MemberStatus.ACTIVE
        );
        Performance p = draftPerformance(manager);

        ReflectionTestUtils.setField(
                p,
                "publicationStatus",
                PublicationStatus.PUBLISHED
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(manager));
        when(performanceRepository.findDetail(PERFORMANCE_ID))
                .thenReturn(Optional.of(p));

        assertFails(
                () -> service.update(
                        MEMBER_ID,
                        PERFORMANCE_ID,
                        updateRequest(0L)
                ),
                ErrorCode.PERFORMANCE_EDIT_LOCKED
        );
    }

    @Test
    @DisplayName("expectedVersion이 현재 버전과 다르면 수정할 수 없다")
    void update_versionConflict() {
        Member manager = member(
                Role.MANAGER,
                MemberStatus.ACTIVE
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(manager));
        when(performanceRepository.findDetail(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(manager)));

        assertFails(
                () -> service.update(
                        MEMBER_ID,
                        PERFORMANCE_ID,
                        updateRequest(5L)
                ),
                ErrorCode.VERSION_CONFLICT
        );
    }

    @Test
    @DisplayName("보낸 필드만 수정되고 보내지 않은 필드는 그대로다")
    void update_success() {
        Member manager = member(
                Role.MANAGER,
                MemberStatus.ACTIVE
        );
        Performance p = draftPerformance(manager);

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(manager));
        when(performanceRepository.findDetail(PERFORMANCE_ID))
                .thenReturn(Optional.of(p));

        PerformanceUpdateRequestDTO dto = updateRequest(0L);

        ReflectionTestUtils.setField(
                dto,
                "title",
                "변경된 제목"
        );

        service.update(
                MEMBER_ID,
                PERFORMANCE_ID,
                dto
        );

        assertThat(p.getTitle())
                .isEqualTo("변경된 제목");
        assertThat(p.getVenueName())
                .isEqualTo("장소");

        verify(performanceRepository).flush();
    }

    @Test
    @DisplayName("ADMIN은 공연을 조회할 수 있다")
    void get_admin_allowed() {
        Member admin = member(
                Role.ADMIN,
                MemberStatus.ACTIVE
        );

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(admin));
        when(performanceRepository.findDetail(PERFORMANCE_ID))
                .thenReturn(Optional.of(draftPerformance(admin)));

        PerformanceResponseDTO result =
                service.get(MEMBER_ID, PERFORMANCE_ID);

        assertThat(result.getTitle())
                .isEqualTo("제목");
    }

    @Test
    @DisplayName("존재하지 않는 장르로는 등록할 수 없다")
    void create_genreNotFound() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(Role.MANAGER, MemberStatus.ACTIVE)
                ));
        when(genreRepository.findActive("THEATER"))
                .thenReturn(Optional.empty());

        assertFails(
                () -> service.create(
                        MEMBER_ID,
                        createRequestDTO(
                                PerformanceSource.MANUAL,
                                null
                        )
                ),
                ErrorCode.VALIDATION_ERROR
        );
    }

    @Test
    @DisplayName("종료일이 시작일보다 빠르면 등록할 수 없다")
    void create_endBeforeStart() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(Role.MANAGER, MemberStatus.ACTIVE)
                ));

        PerformanceCreateRequestDTO dto =
                createRequestDTO(
                        PerformanceSource.MANUAL,
                        null
                );

        ReflectionTestUtils.setField(
                dto,
                "startDate",
                LocalDate.of(2026, 12, 31)
        );
        ReflectionTestUtils.setField(
                dto,
                "endDate",
                LocalDate.of(2026, 12, 1)
        );

        assertFails(
                () -> service.create(MEMBER_ID, dto),
                ErrorCode.VALIDATION_ERROR
        );
    }

    @Test
    @DisplayName("직접 등록(MANUAL)에는 kopisId를 보낼 수 없다")
    void create_manualWithKopisId() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(Role.MANAGER, MemberStatus.ACTIVE)
                ));

        assertFails(
                () -> service.create(
                        MEMBER_ID,
                        createRequestDTO(
                                PerformanceSource.MANUAL,
                                "PF123456"
                        )
                ),
                ErrorCode.VALIDATION_ERROR
        );
    }

    // 이슈 #85 추가 테스트

    @Test
    @DisplayName("공개된 공연은 로그인 없이 상세 정보를 조회할 수 있다")
    void getPublicDetail_success() {
        Performance performance = publicPerformance();

        when(performanceRepository.findPublicDetail(
                PERFORMANCE_ID,
                PublicationStatus.PUBLISHED,
                Visibility.PUBLIC
        )).thenReturn(Optional.of(performance));

        PerformanceResponseDTO result =
                service.getPublicDetail(PERFORMANCE_ID);

        assertThat(result.getTitle()).isEqualTo("제목");
        assertThat(result.getPerformanceId())
                .isEqualTo(PERFORMANCE_ID);

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

        assertFails(
                () -> service.getPublicDetail(PERFORMANCE_ID),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    @DisplayName("공개 공연의 회차 목록을 시작 시간순으로 조회한다")
    void getPublicSessions_success() {
        Performance performance = publicPerformance();

        Instant firstStartsAt =
                Instant.parse("2026-12-01T10:00:00Z");
        Instant secondStartsAt =
                Instant.parse("2026-12-02T10:00:00Z");

        PerformanceSession first =
                session(performance, 101L, firstStartsAt);
        PerformanceSession second =
                session(performance, 102L, secondStartsAt);

        when(performanceRepository.findPublicDetail(
                PERFORMANCE_ID,
                PublicationStatus.PUBLISHED,
                Visibility.PUBLIC
        )).thenReturn(Optional.of(performance));

        when(performanceSessionRepository
                .findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of(first, second));

        List<PerformanceSessionResponseDTO> result =
                service.getPublicSessions(PERFORMANCE_ID);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(PerformanceSessionResponseDTO::sessionId)
                .containsExactly(101L, 102L);

        assertThat(result)
                .extracting(PerformanceSessionResponseDTO::startsAt)
                .containsExactly(firstStartsAt, secondStartsAt);

        verify(performanceSessionRepository)
                .findByPerformance_IdOrderByStartsAtAsc(
                        PERFORMANCE_ID
                );
    }

    @Test
    @DisplayName("공개 공연에 회차가 없으면 빈 목록을 반환한다")
    void getPublicSessions_empty() {
        Performance performance = publicPerformance();

        when(performanceRepository.findPublicDetail(
                PERFORMANCE_ID,
                PublicationStatus.PUBLISHED,
                Visibility.PUBLIC
        )).thenReturn(Optional.of(performance));

        when(performanceSessionRepository
                .findByPerformance_IdOrderByStartsAtAsc(PERFORMANCE_ID))
                .thenReturn(List.of());

        List<PerformanceSessionResponseDTO> result =
                service.getPublicSessions(PERFORMANCE_ID);

        assertThat(result).isEmpty();
    }
}
