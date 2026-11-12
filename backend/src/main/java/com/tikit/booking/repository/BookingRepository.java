package com.tikit.booking.repository;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.payment.entity.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 본인 예매만 조회 (다른 회원 예매는 없는 예매와 같게 처리)
    Optional<Booking> findByIdAndMemberId(Long id, Long memberId);

    // 같은 입장 ID로 이미 만든 예매 (재요청 시 기존 예매 반환)
    Optional<Booking> findByAdmissionIdAndMemberId(UUID admissionId, Long memberId);

    // 내 예매 목록
    Page<Booking> findByMemberId(Long memberId, Pageable pageable);

    // 내 예매 목록 (상태 필터)
    Page<Booking> findByMemberIdAndStatus(Long memberId, BookingStatus status, Pageable pageable);

    // 회원이 회차에 가진 결제 대기와 확정 매수 합계
    @Query("""
            select coalesce(sum(b.seatCount), 0)
            from Booking b
            where b.memberId = :memberId
              and b.sessionId = :sessionId
              and b.status in :statuses
            """)
    long sumSeatCount(@Param("memberId") Long memberId,
                      @Param("sessionId") Long sessionId,
                      @Param("statuses") Collection<BookingStatus> statuses);

    // 만료 처리용 예매 행 잠금 (결제 승인과 같은 순서로 예매부터 잠금)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    // 기한이 지났고 결제 진행 중이 아닌 임시 예매 ID (오래된 순)
    @Query("""
            select b.id
            from Booking b
            where b.status = com.tikit.booking.entity.BookingStatus.PENDING_PAYMENT
              and b.expiresAt <= :now
              and not exists (
                  select 1
                  from PaymentAttempt pa
                  where pa.bookingId = b.id
                    and pa.status in :attemptStatuses
              )
            order by b.expiresAt, b.id
            """)
    List<Long> findExpirableIds(@Param("now") Instant now,
                                @Param("attemptStatuses") Collection<PaymentAttemptStatus> attemptStatuses,
                                Pageable pageable);

    // 회차별 예매 집계
    interface BookingCount {
        Long getSessionId();
        long getBookingCount();
        long getCanceledCount();
        long getRevenue();
    }

    @Query("""
            select b.sessionId as sessionId,
                   sum(case when b.status = com.tikit.booking.entity.BookingStatus.CONFIRMED
                            then 1 else 0 end) as bookingCount,
                   sum(case when b.status = com.tikit.booking.entity.BookingStatus.CANCELED
                            then 1 else 0 end) as canceledCount,
                   coalesce(sum(case when b.status = com.tikit.booking.entity.BookingStatus.CONFIRMED
                                     then b.totalAmount else 0 end), 0) as revenue
            from Booking b
            where b.sessionId in :sessionIds
            group by b.sessionId
            """)
    List<BookingCount> countBySessions(@Param("sessionIds") Collection<Long> sessionIds);
}
