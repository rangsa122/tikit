package com.tikit.booking.repository;

import com.tikit.booking.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 예매 좌석들의 티켓 목록 (예매 단위 티켓 조회용)
    @Query("""
            select t
            from Ticket t
            where t.bookingSeatId in :bookingSeatIds
            """)
    List<Ticket> findByBookingSeatIds(@Param("bookingSeatIds") Collection<Long> bookingSeatIds);
}
