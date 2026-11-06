package com.tikit.booking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EntityListeners(AuditingEntityListener.class)
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    // 대기열 입장권과 1:1 (같은 입장권으로 예매 중복 생성 방지)
    @Column(name = "admission_id", nullable = false, unique = true)
    private UUID admissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(name = "seat_count", nullable = false)
    private Short seatCount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "performance_title_snapshot", nullable = false, length = 200)
    private String performanceTitleSnapshot;

    @Column(name = "starts_at_snapshot", nullable = false)
    private Instant startsAtSnapshot;

    @Column(name = "cancellation_deadline_snapshot", nullable = false)
    private Instant cancellationDeadlineSnapshot;

    @Column(name = "policy_version_snapshot", nullable = false, length = 30)
    private String policyVersionSnapshot;

    // 예매 시점 취소, 환불 정책 (JSON 문자열 그대로 저장)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false)
    private String policySnapshot;

    // 결제 기한 (지나면 만료 대상)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 임시 예매 생성 (상태는 PENDING_PAYMENT, 결제 기한 필수)
    public static Booking create(Long memberId,
                                 Long sessionId,
                                 UUID admissionId,
                                 int seatCount,
                                 Long totalAmount,
                                 String performanceTitleSnapshot,
                                 Instant startsAtSnapshot,
                                 Instant cancellationDeadlineSnapshot,
                                 String policyVersionSnapshot,
                                 String policySnapshot,
                                 Instant expiresAt) {
        if (seatCount <= 0) {
            throw new IllegalArgumentException("좌석 수는 1 이상이어야 합니다. seatCount=" + seatCount);
        }
        if (totalAmount == null || totalAmount < 0) {
            throw new IllegalArgumentException("총 금액은 0 이상이어야 합니다. totalAmount=" + totalAmount);
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("결제 기한(expiresAt)은 필수입니다.");
        }

        Booking booking = new Booking();
        booking.memberId = memberId;
        booking.sessionId = sessionId;
        booking.admissionId = admissionId;
        booking.seatCount = (short) seatCount;
        booking.totalAmount = totalAmount;
        booking.performanceTitleSnapshot = performanceTitleSnapshot;
        booking.startsAtSnapshot = startsAtSnapshot;
        booking.cancellationDeadlineSnapshot = cancellationDeadlineSnapshot;
        booking.policyVersionSnapshot = policyVersionSnapshot;
        booking.policySnapshot = policySnapshot;
        booking.expiresAt = expiresAt;

        return booking;
    }

    public boolean isPendingPayment() {
        return status == BookingStatus.PENDING_PAYMENT;
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    // 결제 기한이 지났는지 (상태와 무관하게 시각만 비교)
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    // 결제 승인 → 예매 확정 (PENDING_PAYMENT에서만 가능)
    public void confirm(Instant now) {
        requireStatus(BookingStatus.PENDING_PAYMENT, "확정");
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    // 결제 기한 초과 → 만료 (PENDING_PAYMENT에서만 가능)
    public void expire() {
        requireStatus(BookingStatus.PENDING_PAYMENT, "만료");
        this.status = BookingStatus.EXPIRED;
    }

    // 결제 전 취소 또는 확정 후 전체 취소 (PENDING_PAYMENT, CONFIRMED에서만 가능)
    public void cancel(Instant now) {
        if (status != BookingStatus.PENDING_PAYMENT && status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("현재 예매 상태에서는 취소할 수 없습니다. status=" + status);
        }
        this.status = BookingStatus.CANCELED;
        this.canceledAt = now;
    }

    private void requireStatus(BookingStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "현재 예매 상태에서는 " + action + "할 수 없습니다. status=" + status + ", expected=" + expected);
        }
    }
}
