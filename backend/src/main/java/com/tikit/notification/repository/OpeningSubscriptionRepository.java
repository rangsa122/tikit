package com.tikit.notification.repository;

import com.tikit.notification.entity.OpeningSubscription;
import com.tikit.notification.entity.OpeningSubscriptionId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


// 공연별 알림 신청 데이터 조회 및 저장
public interface OpeningSubscriptionRepository
        extends JpaRepository<OpeningSubscription, OpeningSubscriptionId> {

    // 회원별 알림 신청 목록 조회
    Page<OpeningSubscription> findByMemberId(Long memberId, Pageable pageable);

    // 특정 공연 알림 신청 여부 확인
    boolean existsByMemberIdAndPerformanceId(Long memberId, Long performanceId);
}
