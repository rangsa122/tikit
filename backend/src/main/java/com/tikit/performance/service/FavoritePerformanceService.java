
package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.Visibility;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.FavoritePerformanceRepository;
import com.tikit.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoritePerformanceService {

    private final FavoritePerformanceRepository favoritePerformanceRepository;
    private final PerformanceRepository performanceRepository;
    private final MemberRepository memberRepository;

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

    @Transactional
    public void removeFavorite(Long memberId, Long performanceId) {
        validateMember(memberId);

        favoritePerformanceRepository.deleteFavorite(
                memberId,
                performanceId
        );
    }

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
