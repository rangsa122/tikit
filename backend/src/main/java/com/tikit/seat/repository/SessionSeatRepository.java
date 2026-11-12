package com.tikit.seat.repository;

import com.tikit.performance.entity.SeatStatus;
import com.tikit.performance.entity.SessionSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    // 회차별 좌석 집계
    interface SeatCount {
        Long getSessionId();
        long getTotalSeats();
        long getSoldSeats();
        long getHeldSeats();
    }

    @Query("""
            select s.session.id as sessionId,
                   count(s) as totalSeats,
                   sum(case when s.status = com.tikit.performance.entity.SeatStatus.SOLD
                            then 1 else 0 end) as soldSeats,
                   sum(case when s.status = com.tikit.performance.entity.SeatStatus.HELD
                            then 1 else 0 end) as heldSeats
            from SessionSeat s
            where s.session.id in :sessionIds
            group by s.session.id
            """)
    List<SeatCount> countBySessions(@Param("sessionIds") Collection<Long> sessionIds);
}
