package com.tikit.seat.repository;

import com.tikit.performance.entity.SessionSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tikit.performance.entity.SeatStatus;
import java.util.List;

public interface SessionSeatRepository extends JpaRepository<SessionSeat, Long> {

    List<SessionSeat> findBySessionId(Long sessionId);
    long countBySessionIdAndStatus(Long sessionId, SeatStatus status);
}
