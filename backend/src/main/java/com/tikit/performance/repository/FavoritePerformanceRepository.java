
package com.tikit.performance.repository;

import com.tikit.performance.entity.FavoritePerformance;
import com.tikit.performance.entity.FavoritePerformanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoritePerformanceRepository
        extends JpaRepository<FavoritePerformance, FavoritePerformanceId> {

    @Modifying
    @Query(value = """
            INSERT INTO favorites (member_id, performance_id)
            VALUES (:memberId, :performanceId)
            ON CONFLICT (member_id, performance_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("memberId") Long memberId,
            @Param("performanceId") Long performanceId
    );

    @Modifying
    @Query(value = """
            DELETE FROM favorites
            WHERE member_id = :memberId
              AND performance_id = :performanceId
            """, nativeQuery = true)
    int deleteFavorite(
            @Param("memberId") Long memberId,
            @Param("performanceId") Long performanceId
    );
}
