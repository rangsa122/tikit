package com.tikit.notification.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

// 예매 시작 알림 처리용 일정 조회
@Repository
@RequiredArgsConstructor
public class SaleOpeningLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<SaleOpeningTarget> findPendingOpenings() {

        String sql = """
                SELECT id, performance_id, opens_at
                FROM sale_openings
                WHERE opens_at <= CURRENT_TIMESTAMP
                  AND notification_processed_at IS NULL
                ORDER BY opens_at, id
                """;

        return jdbcTemplate.query(
                sql,(rs, rowNum) -> new SaleOpeningTarget(
                        rs.getLong("id"),
                        rs.getLong("performance_id"),
                        rs.getObject(
                                "opens_at",
                                OffsetDateTime.class
                        ).toInstant()
                )
        );
    }

    // 알림 처리 완료 시각 기록
    public int markProcessed(Long openingId) {

        String sql = """
                UPDATE sale_openings
                SET notification_processed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND notification_processed_at IS NULL
                """;

        return jdbcTemplate.update(sql, openingId);
    }

    // 알림 처리 대상 일정 정보
    public record SaleOpeningTarget(
            Long openingId,
            Long performanceId,
            Instant opensAt
    ) {
    }
}
