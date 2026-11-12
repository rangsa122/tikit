package com.tikit.booking.repository;

import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.SessionSeat;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 좌석 선점에 필요한 회차, 좌석, 가격 조회와 좌석 상태 변경
public interface SeatHoldRepository extends Repository<SessionSeat, Long> {

    @Query("""
            select s
            from PerformanceSession s
            join fetch s.performance
            where s.id = :sessionId
            """)
    Optional<PerformanceSession> findSessionWithPerformance(@Param("sessionId") Long sessionId);

    // 여러 회차를 공연과 함께 조회 (예매 목록의 공연 ID용)
    @Query("""
            select s
            from PerformanceSession s
            join fetch s.performance
            where s.id in :sessionIds
            """)
    List<PerformanceSession> findSessionsWithPerformance(@Param("sessionIds") Collection<Long> sessionIds);

    @Query("""
            select o.opensAt
            from SaleOpening o
            where o.id = :openingId
            """)
    Optional<Instant> findOpensAt(@Param("openingId") Long openingId);

    @Query("""
            select s
            from SessionSeat s
            join fetch s.templateSeat
            where s.session.id = :sessionId
              and s.id in :seatIds
            """)
    List<SessionSeat> findSeats(@Param("sessionId") Long sessionId,
                                @Param("seatIds") Collection<Long> seatIds);

    @Query("""
            select p
            from SessionGradePrice p
            where p.id.sessionId = :sessionId
            """)
    List<SessionGradePrice> findGradePrices(@Param("sessionId") Long sessionId);

    // 아직 AVAILABLE인 좌석만 HELD로 변경, 변경된 좌석 수 반환
    @Modifying(flushAutomatically = true)
    @Query("""
            update SessionSeat s
            set s.status = com.tikit.performance.entity.SeatStatus.HELD,
                s.currentBookingId = :bookingId,
                s.version = s.version + 1,
                s.updatedAt = :now
            where s.session.id = :sessionId
              and s.id in :seatIds
              and s.status = com.tikit.performance.entity.SeatStatus.AVAILABLE
            """)
    int holdSeats(@Param("sessionId") Long sessionId,
                  @Param("seatIds") Collection<Long> seatIds,
                  @Param("bookingId") Long bookingId,
                  @Param("now") Instant now);

    // 이 예매가 선점한 좌석만 AVAILABLE로 변경, 변경된 좌석 수 반환
    @Modifying(flushAutomatically = true)
    @Query("""
            update SessionSeat s
            set s.status = com.tikit.performance.entity.SeatStatus.AVAILABLE,
                s.currentBookingId = null,
                s.version = s.version + 1,
                s.updatedAt = :now
            where s.id in :seatIds
              and s.currentBookingId = :bookingId
              and s.status = com.tikit.performance.entity.SeatStatus.HELD
            """)
    int releaseSeats(@Param("seatIds") Collection<Long> seatIds,
                     @Param("bookingId") Long bookingId,
                     @Param("now") Instant now);

    // 이 예매가 선점한 좌석만 SOLD로 변경, 변경된 좌석 수 반환
    @Modifying(flushAutomatically = true)
    @Query("""
            update SessionSeat s
            set s.status = com.tikit.performance.entity.SeatStatus.SOLD,
                s.version = s.version + 1,
                s.updatedAt = :now
            where s.id in :seatIds
              and s.currentBookingId = :bookingId
              and s.status = com.tikit.performance.entity.SeatStatus.HELD
            """)
    int sellSeats(@Param("seatIds") Collection<Long> seatIds,
                  @Param("bookingId") Long bookingId,
                  @Param("now") Instant now);

    // 이 예매에 판매된 좌석만 AVAILABLE로 되돌림 (전체 취소), 변경된 좌석 수 반환
    @Modifying(flushAutomatically = true)
    @Query("""
            update SessionSeat s
            set s.status = com.tikit.performance.entity.SeatStatus.AVAILABLE,
                s.currentBookingId = null,
                s.version = s.version + 1,
                s.updatedAt = :now
            where s.id in :seatIds
              and s.currentBookingId = :bookingId
              and s.status = com.tikit.performance.entity.SeatStatus.SOLD
            """)
    int releaseSoldSeats(@Param("seatIds") Collection<Long> seatIds,
                         @Param("bookingId") Long bookingId,
                         @Param("now") Instant now);
}
