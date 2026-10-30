package com.tikit.seat.repository;

import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.SessionGradePriceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionGradePriceRepository
        extends JpaRepository<SessionGradePrice, SessionGradePriceId> {

    List<SessionGradePrice> findBySessionId(Long sessionId);
}
