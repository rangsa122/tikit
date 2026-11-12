package com.tikit.performance.repository;

import com.tikit.performance.entity.SaleOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface SaleOpeningRepository extends JpaRepository<SaleOpening, Long> {
    List<SaleOpening> findByPerformance_IdOrderByOpensAtAsc(Long performanceId);

    @Query("select o from SaleOpening o where o.performance.id = :performanceId")
    List<SaleOpening> findByPerformance(@Param("performanceId") Long performanceId);

    /**
     * 여러 공연의 판매 오픈 일정을 한 번에 조회
     */
    @Query("""
            select o
            from SaleOpening o
            where o.performance.id in :performanceIds
            """)
    List<SaleOpening> findByPerformanceIds(
            @Param("performanceIds") Collection<Long> performanceIds
    );
}