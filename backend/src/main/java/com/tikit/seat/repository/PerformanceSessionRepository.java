package com.tikit.seat.repository;

import com.tikit.performance.entity.PerformanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerformanceSessionRepository
        extends JpaRepository<PerformanceSession, Long> {

    @Query("select s from PerformanceSession s where s.performance.id = :performanceId order by s.startsAt asc")
    List<PerformanceSession> findByPerformance(@Param("performanceId") Long performanceId);

    /**
     * 특정 공연의 회차 목록을 공연 시작 시간순으로 조회
     */
    List<PerformanceSession> findByPerformance_IdOrderByStartsAtAsc(
            Long performanceId
    );
}
