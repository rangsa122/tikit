package com.tikit.payment.repository;

import com.tikit.auth.entity.Member;
import com.tikit.booking.entity.Booking;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByOrderId(String orderId);

    // 회원 본인의 결제 시도를 예매·회원 정보와 함께 조회 (세 테이블을 한 번에 읽기 위해 네이티브 쿼리 사용)
    @Query(value = """
            SELECT pa.id                          AS "paymentAttemptId",
                   pa.booking_id                  AS "bookingId",
                   pa.order_id                    AS "orderId",
                   m.customer_key                 AS "customerKey",
                   pa.amount                      AS "amount",
                   pa.currency                    AS "currency",
                   pa.status                      AS "status",
                   pa.failure_code                AS "failureCode",
                   b.status                       AS "bookingStatus",
                   b.expires_at                   AS "expiresAt",
                   b.performance_title_snapshot   AS "performanceTitle",
                   b.seat_count::int              AS "seatCount"
            FROM payment_attempts pa
            JOIN bookings b ON b.id = pa.booking_id
            JOIN members m ON m.id = b.member_id
            WHERE pa.id = :paymentAttemptId
              AND b.member_id = :memberId
            """, nativeQuery = true)
    Optional<PaymentAttemptDetailView> findDetailByIdAndMemberId(
            @Param("paymentAttemptId") Long paymentAttemptId,
            @Param("memberId") Long memberId
    );

    // 결제 준비: 본인 예매를 조회하면서 예매 행만 잠금 (회원 상태·역할을 함께 읽기 위해 네이티브 쿼리 사용)
    @Query(value = """
            SELECT b.id           AS "bookingId",
                   b.status       AS "status",
                   b.expires_at   AS "expiresAt",
                   b.total_amount AS "totalAmount",
                   m.status       AS "memberStatus",
                   m.role         AS "memberRole"
            FROM bookings b
            JOIN members m ON m.id = b.member_id
            WHERE b.id = :bookingId
              AND b.member_id = :memberId
            FOR UPDATE OF b
            """, nativeQuery = true)
    Optional<BookingPaymentView> findBookingForUpdate(
            @Param("bookingId") Long bookingId,
            @Param("memberId") Long memberId
    );

    // 같은 예매에 해당 상태의 결제 시도가 있는지 확인 (진행 중 결제 확인용)
    boolean existsByBookingIdAndStatusIn(Long bookingId, Collection<PaymentAttemptStatus> statuses);

    // 같은 예매의 해당 상태 결제 시도 조회 (READY 재사용용)
    Optional<PaymentAttempt> findFirstByBookingIdAndStatus(Long bookingId, PaymentAttemptStatus status);

    // 승인 1. 결제 시도의 예매 ID만 조회 (잠금 순서를 지키려고 잠금 없이 먼저 읽음)
    @Query("SELECT pa.bookingId FROM PaymentAttempt pa WHERE pa.id = :paymentAttemptId")
    Optional<Long> findBookingIdById(@Param("paymentAttemptId") Long paymentAttemptId);

    // 승인 2. 회원 행 잠금 (승인 중 탈퇴 같은 회원의 다른 작업과 겹치지 않게)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findMemberForUpdate(@Param("memberId") Long memberId);

    // 승인 3. 본인 예매를 조회하면서 예매 행만 잠금 (결제 준비에서도 사용)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId AND b.memberId = :memberId")
    Optional<Booking> findOwnedBookingForUpdate(
            @Param("bookingId") Long bookingId,
            @Param("memberId") Long memberId
    );

    // 승인 4. 결제 시도 행 잠금 후 엔티티로 조회 (상태를 PROCESSING으로 바꿔야 해서 엔티티로 받음)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pa FROM PaymentAttempt pa WHERE pa.id = :paymentAttemptId")
    Optional<PaymentAttempt> findByIdForUpdate(@Param("paymentAttemptId") Long paymentAttemptId);
}