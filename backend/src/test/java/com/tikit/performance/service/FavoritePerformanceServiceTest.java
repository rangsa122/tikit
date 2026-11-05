
package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.Visibility;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.FavoritePerformanceRepository;
import com.tikit.performance.repository.PerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoritePerformanceServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PERFORMANCE_ID = 10L;

    @Mock
    private FavoritePerformanceRepository favoritePerformanceRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private FavoritePerformanceService favoritePerformanceService;

    private Member member;
    private Performance performance;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        performance = mock(Performance.class);
    }

    private void stubActiveMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        when(member.getStatus())
                .thenReturn(MemberStatus.ACTIVE);
    }

    private void stubPublicPerformance() {
        when(performanceRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(performance));

        when(performance.getPublicationStatus())
                .thenReturn(PublicationStatus.PUBLISHED);

        when(performance.getVisibility())
                .thenReturn(Visibility.PUBLIC);
    }

    @Test
    void addFavorite_success() {
        stubActiveMember();
        stubPublicPerformance();

        when(favoritePerformanceRepository.insertIfAbsent(
                MEMBER_ID, PERFORMANCE_ID
        )).thenReturn(1);

        assertThatCode(() ->
                favoritePerformanceService.addFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).doesNotThrowAnyException();

        verify(favoritePerformanceRepository)
                .insertIfAbsent(MEMBER_ID, PERFORMANCE_ID);
    }

    @Test
    void addFavorite_duplicateRequest_success() {
        stubActiveMember();
        stubPublicPerformance();

        when(favoritePerformanceRepository.insertIfAbsent(
                MEMBER_ID, PERFORMANCE_ID
        )).thenReturn(0);

        assertThatCode(() ->
                favoritePerformanceService.addFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).doesNotThrowAnyException();

        verify(favoritePerformanceRepository)
                .insertIfAbsent(MEMBER_ID, PERFORMANCE_ID);
    }

    @Test
    void addFavorite_performanceNotFound() {
        stubActiveMember();

        when(performanceRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                favoritePerformanceService.addFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(favoritePerformanceRepository);
    }

    @Test
    void addFavorite_privatePerformance() {
        stubActiveMember();

        when(performanceRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(performance));

        when(performance.getPublicationStatus())
                .thenReturn(PublicationStatus.PUBLISHED);

        when(performance.getVisibility())
                .thenReturn(Visibility.PRIVATE);

        assertThatThrownBy(() ->
                favoritePerformanceService.addFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(favoritePerformanceRepository);
    }

    @Test
    void addFavorite_unpublishedPerformance() {
        stubActiveMember();

        when(performanceRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(performance));

        when(performance.getPublicationStatus())
                .thenReturn(PublicationStatus.DRAFT);

        assertThatThrownBy(() ->
                favoritePerformanceService.addFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(favoritePerformanceRepository);
    }

    @Test
    void addFavorite_inactiveMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        when(member.getStatus())
                .thenReturn(MemberStatus.SUSPENDED);

        assertThatThrownBy(() ->
                favoritePerformanceService.addFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(
                performanceRepository,
                favoritePerformanceRepository
        );
    }

    @Test
    void addFavorite_memberNotFound() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                favoritePerformanceService.addFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(
                performanceRepository,
                favoritePerformanceRepository
        );
    }

    @Test
    void addFavorite_nullMemberId() {
        assertThatThrownBy(() ->
                favoritePerformanceService.addFavorite(
                        null, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(
                memberRepository,
                performanceRepository,
                favoritePerformanceRepository
        );
    }

    @Test
    void removeFavorite_success() {
        stubActiveMember();

        when(favoritePerformanceRepository.deleteFavorite(
                MEMBER_ID, PERFORMANCE_ID
        )).thenReturn(1);

        assertThatCode(() ->
                favoritePerformanceService.removeFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).doesNotThrowAnyException();

        verify(favoritePerformanceRepository)
                .deleteFavorite(MEMBER_ID, PERFORMANCE_ID);
    }

    @Test
    void removeFavorite_alreadyRemoved_success() {
        stubActiveMember();

        when(favoritePerformanceRepository.deleteFavorite(
                MEMBER_ID, PERFORMANCE_ID
        )).thenReturn(0);

        assertThatCode(() ->
                favoritePerformanceService.removeFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).doesNotThrowAnyException();

        verify(favoritePerformanceRepository)
                .deleteFavorite(MEMBER_ID, PERFORMANCE_ID);
    }

    @Test
    void removeFavorite_onlyCurrentMember() {
        stubActiveMember();

        favoritePerformanceService.removeFavorite(
                MEMBER_ID, PERFORMANCE_ID
        );

        verify(favoritePerformanceRepository)
                .deleteFavorite(MEMBER_ID, PERFORMANCE_ID);

        verify(favoritePerformanceRepository, never())
                .deleteFavorite(2L, PERFORMANCE_ID);
    }

    @Test
    void removeFavorite_inactiveMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        when(member.getStatus())
                .thenReturn(MemberStatus.SUSPENDED);

        assertThatThrownBy(() ->
                favoritePerformanceService.removeFavorite(
                        MEMBER_ID, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(favoritePerformanceRepository);
    }

    @Test
    void removeFavorite_nullMemberId() {
        assertThatThrownBy(() ->
                favoritePerformanceService.removeFavorite(
                        null, PERFORMANCE_ID
                )
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(
                memberRepository,
                favoritePerformanceRepository
        );
    }
}
