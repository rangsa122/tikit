
package com.tikit.seat.repository;

import com.tikit.performance.entity.PerformanceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceSessionRepository
        extends JpaRepository<PerformanceSession, Long> {

    /**
     * 특정 공연의 회차 목록을 공연 시작 시간순으로 조회
     */
    List<PerformanceSession> findByPerformance_IdOrderByStartsAtAsc(
            Long performanceId
    );
}
