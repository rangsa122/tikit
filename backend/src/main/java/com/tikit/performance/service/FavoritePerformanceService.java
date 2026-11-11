
package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.response.FavoritePerformanceResponseDTO;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.SaleOpening;
import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.Visibility;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.FavoritePerformanceRepository;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.performance.repository.SaleOpeningRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoritePerformanceService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    private final FavoritePerformanceRepository favoritePerformanceRepository;
    private final PerformanceRepository performanceRepository;
    private final MemberRepository memberRepository;
    private final PerformanceSessionRepository performanceSessionRepository;
    private final SessionGradePriceRepository sessionGradePriceRepository;
    private final SaleOpeningRepository saleOpeningRepository;

    // 관심 공연 등록
    @Transactional
    public void addFavorite(Long memberId, Long performanceId) {
        validateMember(memberId);

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        if (performance.getPublicationStatus() != PublicationStatus.PUBLISHED
                || performance.getVisibility() != Visibility.PUBLIC) {
            throw new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        favoritePerformanceRepository.insertIfAbsent(
                memberId,
                performanceId
        );
    }

    // 관심 공연 해제
    @Transactional
    public void removeFavorite(Long memberId, Long performanceId) {
        validateMember(memberId);

        favoritePerformanceRepository.deleteFavorite(
                memberId,
                performanceId
        );
    }

    // 관심 공연 목록 페이지 조회
    @Transactional(readOnly = true)
    public Page<FavoritePerformanceResponseDTO> getFavorites(
            Long memberId,
            int page,
            int size
    ) {
        validateMember(memberId);

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);

        Page<Performance> performances = favoritePerformanceRepository
                .findPublicFavorites(
                        memberId,
                        PublicationStatus.PUBLISHED,
                        Visibility.PUBLIC,
                        PageRequest.of(safePage, safeSize)
                )
                .map(favorite -> favorite.getPerformance());

        return performances.map(this::toFavoriteResponse);
    }

    // 관심 공연 응답 DTO 변환
    private FavoritePerformanceResponseDTO toFavoriteResponse(
            Performance performance
    ) {
        Long performanceId = performance.getId();

        List<PerformanceSession> sessions =
                performanceSessionRepository
                        .findByPerformance_IdOrderByStartsAtAsc(performanceId);

        List<Long> sessionIds = sessions.stream()
                .map(PerformanceSession::getId)
                .toList();

        List<SessionGradePrice> prices = sessionIds.isEmpty()
                ? List.of()
                : sessionGradePriceRepository.findBySessionIdIn(sessionIds);

        Long minPrice = prices.stream()
                .map(SessionGradePrice::getPrice)
                .min(Long::compareTo)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        Instant now = Instant.now();

        OffsetDateTime nextSalesOpenAt = saleOpeningRepository
                .findByPerformance_IdOrderByOpensAtAsc(performanceId)
                .stream()
                .map(SaleOpening::getOpensAt)
                .filter(openAt -> openAt.isAfter(now))
                .min(Instant::compareTo)
                .map(openAt ->
                        openAt.atZone(RESPONSE_ZONE).toOffsetDateTime()
                )
                .orElse(null);

        return FavoritePerformanceResponseDTO.from(
                performance,
                minPrice,
                nextSalesOpenAt
        );
    }

    // 회원 유효성 검사
    private void validateMember(Long memberId) {
        if (memberId == null) {
            throw new PerformanceException(
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.AUTHENTICATION_REQUIRED)
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new PerformanceException(
                    ErrorCode.ACCOUNT_RESTRICTED
            );
        }
    }
}
