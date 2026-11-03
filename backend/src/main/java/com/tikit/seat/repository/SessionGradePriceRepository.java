package com.tikit.seat.repository;

import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.SessionGradePriceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SessionGradePriceRepository
        extends JpaRepository<SessionGradePrice, SessionGradePriceId> {

    List<SessionGradePrice> findBySessionId(Long sessionId);

    @Query("select p from SessionGradePrice p where p.id.sessionId in :sessionIds")
    List<SessionGradePrice> findBySessions(@Param("sessionIds") Collection<Long> sessionIds);
}
