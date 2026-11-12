package com.tikit.notification.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
