
package com.tikit.performance.repository;

import com.tikit.performance.entity.FavoritePerformance;
import com.tikit.performance.entity.FavoritePerformanceId;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoritePerformanceRepository
        extends JpaRepository<FavoritePerformance, FavoritePerformanceId> {

    // 관심 공연 등록 (중복 등록 방지)
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

    // 관심 공연 해제
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

    // 로그인한 사용자의 공개 관심 공연 목록 조회
    @Query("""
            SELECT f
            FROM FavoritePerformance f
            JOIN FETCH f.performance p
            WHERE f.id.memberId = :memberId
              AND p.publicationStatus = :publicationStatus
              AND p.visibility = :visibility
            ORDER BY f.createdAt DESC
            """)
    List<FavoritePerformance> findPublicFavorites(
            @Param("memberId") Long memberId,
            @Param("publicationStatus") PublicationStatus publicationStatus,
            @Param("visibility") Visibility visibility
    );
}
