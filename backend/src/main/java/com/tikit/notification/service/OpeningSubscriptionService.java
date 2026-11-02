package com.tikit.notification.service;

import com.tikit.notification.entity.OpeningSubscription;
import com.tikit.notification.entity.OpeningSubscriptionId;
import com.tikit.notification.repository.OpeningSubscriptionRepository;
import com.tikit.notification.repository.PerformanceLookupRepository;

import com.tikit.auth.entity.Member;
import com.tikit.auth.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.NoSuchElementException;


// 공연별 예매 시작 알림 신청 관리
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpeningSubscriptionService {

    private final OpeningSubscriptionRepository subscriptionRepository;
    private final PerformanceLookupRepository performanceLookupRepository;
    private final MemberRepository memberRepository;

    // 공연별 알림 신청
    @Transactional
    public void subscribe(Long memberId, Long performanceId) {

        if (!performanceLookupRepository.existsById(performanceId)) {
            throw new NoSuchElementException("존재하지 않는 공연입니다.");
        }

        OpeningSubscriptionId id =
                new OpeningSubscriptionId(memberId, performanceId);

        // 이미 신청한 경우 - 유지
        if (subscriptionRepository.existsById(id)) {
            return;
        }

        OpeningSubscription subscription =
                new OpeningSubscription(memberId, performanceId);

        subscriptionRepository.save(subscription);
    }

    // 공연별 알림 신청 <<해제>>
    @Transactional
    public void unsubscribe(Long memberId, Long performanceId) {

        OpeningSubscriptionId id = new OpeningSubscriptionId(memberId, performanceId);

        subscriptionRepository.findById(id)
                .ifPresent(subscriptionRepository::delete);
    }

    // 회원별 알림 신청 목록 페이지네이션 조회
    public Page<OpeningSubscription> getSubscriptions(Long memberId, int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "페이지 번호 또는 크기가 올바르지 않습니다."
            );
        }

        // 공연 ID 정렬
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("performanceId").ascending()
        );

        // DB에서 필요한 페이지만 조회
        return subscriptionRepository.findByMemberId(memberId, pageable);
    }

    // 특정 공연 알림 신청 여부 조회
    public boolean isSubscribed(Long memberId, Long performanceId) {
        return subscriptionRepository.existsByMemberIdAndPerformanceId(
                memberId, performanceId
        );
    }

    // 전체 알림 수신 설정 조회
    public boolean getNotificationEnabled(Long memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "회원을 찾을 수 없습니다."
                        )
                );

        return member.isNotificationEnabled();
    }

    // 전체 알림 수신 설정 <<변경>>
    @Transactional
    public boolean updateNotificationEnabled(Long memberId, boolean enabled) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "회원을 찾을 수 없습니다."
                        )
                );

        member.changeNotificationEnabled(enabled);

        return member.isNotificationEnabled();
    }
}
