package com.tikit.notification.repository;

import com.tikit.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    // 예매 시작 일정 ID와 공연 제목 매핑
    interface OpeningPerformanceTitle {
        Long getOpeningId();
        String getPerformanceTitle();
    }

    // 여러 알림의 공연 제목을 한 번에 조회
    @Query(value = """
            SELECT
                o.id AS "openingId",
                p.title AS "performanceTitle"
            FROM sale_openings o
            JOIN performances p
                ON p.id = o.performance_id
            WHERE o.id IN (:openingIds)
            """, nativeQuery = true)
    List<OpeningPerformanceTitle> findPerformanceTitlesByOpeningIds(
            @Param("openingIds") Collection<Long> openingIds
    );
}
