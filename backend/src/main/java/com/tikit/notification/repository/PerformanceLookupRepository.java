package com.tikit.notification.repository;

import com.tikit.notification.dto.OpeningSubscriptionItemResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

// 공연 존재 여부 조회
@Repository
@RequiredArgsConstructor
public class PerformanceLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean existsById(Long performanceId) {

        String sql = """
                SELECT COUNT(*)
                FROM performances
                WHERE id = ?
                """;

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                performanceId
        );

        return count != null && count > 0;
    }

    // 예매 오픈 전 공연별 알림 신청 목록 조회
    public Page<OpeningSubscriptionItemResponseDTO> findUpcomingSubscriptions(
            Long memberId,
            Pageable pageable
    ) {
        // 예매 오픈 전 공연의 전체 건수 조회
        String countSql = """
                SELECT COUNT(*)
                FROM opening_subscriptions os
                JOIN performances p ON p.id = os.performance_id
                WHERE os.member_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM sale_openings so
                      WHERE so.performance_id = p.id
                        AND so.opens_at > CURRENT_TIMESTAMP
                  )
                """;

        Long totalElements = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                memberId
        );

        // 공연 정보와 가장 가까운 예매 오픈일 조회
        String sql = """
                SELECT
                    p.id AS performance_id,
                    p.title,
                    p.venue_name,
                    p.start_date,
                    p.end_date,
                    MIN(so.opens_at) AS opens_at
                FROM opening_subscriptions os
                JOIN performances p ON p.id = os.performance_id
                JOIN sale_openings so
                    ON so.performance_id = p.id
                   AND so.opens_at > CURRENT_TIMESTAMP
                WHERE os.member_id = ?
                GROUP BY
                    p.id,
                    p.title,
                    p.venue_name,
                    p.start_date,
                    p.end_date
                ORDER BY MIN(so.opens_at), p.id
                LIMIT ? OFFSET ?
                """;

        List<OpeningSubscriptionItemResponseDTO> items =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) ->
                                new OpeningSubscriptionItemResponseDTO(
                                        rs.getLong("performance_id"),
                                        rs.getString("title"),
                                        rs.getString("venue_name"),
                                        rs.getDate("start_date").toLocalDate(),
                                        rs.getDate("end_date").toLocalDate(),
                                        rs.getTimestamp("opens_at").toInstant()
                                ),
                        memberId,
                        pageable.getPageSize(),
                        pageable.getOffset()
                );

        // 페이지네이션 응답 생성
        return new PageImpl<>(
                items,
                pageable,
                totalElements == null ? 0L : totalElements
        );
    }
}
