
package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.response.FavoritePerformanceResponseDTO;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.Visibility;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.FavoritePerformanceRepository;
import com.tikit.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoritePerformanceService {

    private final FavoritePerformanceRepository favoritePerformanceRepository;
    private final PerformanceRepository performanceRepository;
    private final MemberRepository memberRepository;

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

    // 관심 공연 목록 조회 (신규)
    @Transactional(readOnly = true)
    public List<FavoritePerformanceResponseDTO> getFavorites(Long memberId) {
        validateMember(memberId);

        return favoritePerformanceRepository.findPublicFavorites(
                        memberId,
                        PublicationStatus.PUBLISHED,
                        Visibility.PUBLIC
                )
                .stream()
                .map(favorite ->
                        FavoritePerformanceResponseDTO.from(
                                favorite.getPerformance()
                        )
                )
                .toList();
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
                        new PerformanceException(
                                ErrorCode.AUTHENTICATION_REQUIRED
                        )
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new PerformanceException(
                    ErrorCode.ACCOUNT_RESTRICTED
            );
        }
    }
}
