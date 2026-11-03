package com.tikit.booking.repository;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 본인 예매만 조회 (다른 회원 예매는 없는 예매와 같게 처리)
    Optional<Booking> findByIdAndMemberId(Long id, Long memberId);

    // 같은 입장 ID로 이미 만든 예매 (재요청 시 기존 예매 반환)
    Optional<Booking> findByAdmissionIdAndMemberId(UUID admissionId, Long memberId);

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
}
