package com.tikit.notification.service;

import com.tikit.notification.repository.SaleOpeningLookupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

// 공연 예매 시작 시 사이트 내부 알림 생성
@Service
@RequiredArgsConstructor
public class OpeningNotificationDispatchService {

    private final SaleOpeningLookupRepository saleOpeningRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public void dispatchPendingNotifications() {

        List<SaleOpeningLookupRepository.SaleOpeningTarget> targets = saleOpeningRepository.findPendingOpenings();

        for (var target : targets) {
            transactionTemplate.executeWithoutResult(status -> {
                processOpening(target.openingId(), target.performanceId());
            });
        }
    }

    // 한 예매 시작 일정의 알림 생성 및 처리
    private void processOpening(Long openingId, Long performanceId) {

        // 다른 작업이 처리 중인 일정은 건너뜀
        String lockSql = """
                SELECT id
                FROM sale_openings
                WHERE id = ?
                  AND opens_at <= CURRENT_TIMESTAMP
                  AND notification_processed_at IS NULL
                FOR UPDATE SKIP LOCKED
                """;

        List<Long> lockedIds = jdbcTemplate.queryForList(
                lockSql,
                Long.class,
                openingId
        );

        if (lockedIds.isEmpty()) {
            return;
        }

        // 신청 회원 중 전체 알림 수신 ON인 회원에게 알림 저장
        String insertSql = """
                INSERT INTO notifications (
                    member_id,
                    opening_id,
                    type,
                    title,
                    message
                )
                SELECT
                    s.member_id,
                    ?,
                    'SALE_OPENING',
                    '예매 시작 알림',
                    '신청한 공연의 예매가 시작되었습니다.'
                FROM opening_subscriptions s
                JOIN members m ON m.id = s.member_id
                WHERE s.performance_id = ?
                  AND m.notification_enabled = TRUE
                ON CONFLICT (member_id, opening_id, type)
                DO NOTHING
                """;

        jdbcTemplate.update(insertSql, openingId, performanceId);

        // 알림 저장 후 처리 완료 기록
        saleOpeningRepository.markProcessed(openingId);
    }
}
