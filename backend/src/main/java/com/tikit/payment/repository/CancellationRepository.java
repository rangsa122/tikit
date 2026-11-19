package com.tikit.payment.repository;

import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.Collection;
import java.util.List;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

    // 여러 결제 시도의 취소를 최신순으로 조회 (예매 조회의 최신 취소 상태용)
    List<Cancellation> findByPaymentAttemptIdInOrderByIdDesc(Collection<Long> paymentAttemptIds);

    // 이 결제에 해당 상태의 취소가 있는지 (진행 중 취소 확인용)
    boolean existsByPaymentAttemptIdAndStatusIn(Long paymentAttemptId, Collection<CancellationStatus> statuses);

    // 회원의 진행 중인 취소·환불 존재 여부
    @Query("""
        SELECT (COUNT(c) > 0)
        FROM Cancellation c
        JOIN PaymentAttempt pa ON pa.id = c.paymentAttemptId
        JOIN Booking b ON b.id = pa.bookingId
        WHERE b.memberId = :memberId
          AND c.status IN :statuses
        """)
    boolean existsByMemberIdAndStatusIn(
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<CancellationStatus> statuses
    );

    // 관리자 취소·환불 내역: 예매 ID·상태는 선택 (null이면 조건 없음), 최신순 (예매 ID는 결제 시도를 거쳐 거름)
    @Query(value = """
            SELECT c FROM Cancellation c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:bookingId IS NULL OR c.paymentAttemptId IN (
                    SELECT pa.id FROM PaymentAttempt pa WHERE pa.bookingId = :bookingId))
            ORDER BY c.id DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM Cancellation c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:bookingId IS NULL OR c.paymentAttemptId IN (
                    SELECT pa.id FROM PaymentAttempt pa WHERE pa.bookingId = :bookingId))
            """)
    Page<Cancellation> searchForAdmin(
            @Param("bookingId") Long bookingId,
            @Param("status") CancellationStatus status,
            Pageable pageable
    );

    // 관리자 목록: 한 페이지의 결제 시도를 한 번에 조회 (취소 -> 예매 ID 연결용, N+1 방지)
    @Query("SELECT pa FROM PaymentAttempt pa WHERE pa.id IN :ids")
    List<PaymentAttempt> findPaymentAttemptsByIds(@Param("ids") Collection<Long> ids);

    // 취소 행 잠금 (2차 기록이 동시에 와도 하나씩 처리)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cancellation c WHERE c.id = :id")
    Optional<Cancellation> findByIdForUpdate(@Param("id") Long id);

    // 회원 본인 예매의 취소면 그 예매 ID를 조회 (취소 -> 결제 시도 -> 예매를 따라가 본인 확인)
    @Query("""
            SELECT pa.bookingId
            FROM Cancellation c
            JOIN PaymentAttempt pa ON pa.id = c.paymentAttemptId
            JOIN Booking b ON b.id = pa.bookingId
            WHERE c.id = :cancellationId
              AND b.memberId = :memberId
            """)
    Optional<Long> findOwnedBookingId(
            @Param("cancellationId") Long cancellationId,
            @Param("memberId") Long memberId
    );

    // 관리자: 취소의 예매 ID 조회 (취소 -> 결제 시도 -> 예매, 주인 상관없음)
    @Query("""
            SELECT pa.bookingId
            FROM Cancellation c
            JOIN PaymentAttempt pa ON pa.id = c.paymentAttemptId
            WHERE c.id = :cancellationId
            """)
    Optional<Long> findBookingIdById(@Param("cancellationId") Long cancellationId);
}