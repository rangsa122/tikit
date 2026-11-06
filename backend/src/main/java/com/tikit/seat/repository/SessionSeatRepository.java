package com.tikit.seat.repository;

import com.tikit.performance.entity.SessionSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import com.tikit.performance.entity.SeatStatus;
import java.util.List;

public interface SessionSeatRepository extends JpaRepository<SessionSeat, Long> {

    List<SessionSeat> findBySessionId(Long sessionId);

    @Query("select count(s) > 0 from SessionSeat s " +
            "where s.session.id in :sessionIds " +
            "and s.status <> com.tikit.performance.entity.SeatStatus.AVAILABLE")
    boolean existsTaken(@Param("sessionIds") Collection<Long> sessionIds);

    @Modifying
    @Query("delete from SessionSeat s where s.session.id in :sessionIds")
    void deleteBySessions(@Param("sessionIds") Collection<Long> sessionIds);
    long countBySessionIdAndStatus(Long sessionId, SeatStatus status);
}
