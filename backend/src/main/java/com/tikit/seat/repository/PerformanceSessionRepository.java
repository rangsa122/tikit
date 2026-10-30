package com.tikit.seat.repository;

import com.tikit.performance.entity.PerformanceSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceSessionRepository
        extends JpaRepository<PerformanceSession, Long> {
}
