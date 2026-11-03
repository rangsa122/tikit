package com.tikit.notification.repository;

import com.tikit.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 사이트 내부 알림 DB 접근
public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    // 회원의 <<전체>> 알림 목록 조회
    Page<Notification> findByMemberId(
            Long memberId,
            Pageable pageable
    );

    // 회원의 <<읽지 않은>> 알림 목록 조회
    Page<Notification> findByMemberIdAndReadAtIsNull(
            Long memberId,
            Pageable pageable
    );

    // 회원 본인의 알림만 조회
    Optional<Notification> findByIdAndMemberId(
            Long id,
            Long memberId
    );
}
