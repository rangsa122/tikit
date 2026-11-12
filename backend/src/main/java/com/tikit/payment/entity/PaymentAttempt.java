package com.tikit.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(name = "pg_idempotency_key", nullable = false, unique = true, length = 128)
    private String pgIdempotencyKey;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KRW";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentAttemptStatus status = PaymentAttemptStatus.READY;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Column(name = "confirmation_requested_at")
    private Instant confirmationRequestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaymentAttempt create(Long bookingId, String orderId, Long amount, String pgIdempotencyKey) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.bookingId = bookingId;
        attempt.orderId = orderId;
        attempt.amount = amount;
        attempt.pgIdempotencyKey = pgIdempotencyKey;

        return attempt;
    }

    public boolean isReady() {
        return status == PaymentAttemptStatus.READY;
    }

    public boolean isApproved() {
        return status == PaymentAttemptStatus.APPROVED;
    }

    // 승인 결과를 아직 모르는 상태 (처리 중 또는 결과 확인 중)
    public boolean isInProgress() {
        return status == PaymentAttemptStatus.PROCESSING || status == PaymentAttemptStatus.RECONCILING;
    }

    // 승인 요청 시작: READY -> PROCESSING (Toss 호출 전에 저장, 복구 작업이 조회할 paymentKey도 함께 저장)
    public void startConfirmation(String paymentKey, Instant now) {
        if (status != PaymentAttemptStatus.READY) {
            throw new IllegalStateException(
                    "READY 상태에서만 승인을 시작할 수 있습니다. status=" + status);
        }
        this.paymentKey = paymentKey;
        this.confirmationRequestedAt = now;
        this.status = PaymentAttemptStatus.PROCESSING;
    }

    // 승인 완료: PROCESSING, RECONCILING -> APPROVED
    public void approve(Instant approvedAt) {
        requireInProgress("승인 완료");
        this.approvedAt = approvedAt;
        this.status = PaymentAttemptStatus.APPROVED;
    }

    // 승인 실패 확정: PROCESSING, RECONCILING -> FAILED
    public void fail(String failureCode) {
        requireInProgress("승인 실패");
        this.failureCode = failureCode;
        this.status = PaymentAttemptStatus.FAILED;
    }

    // 결과 불명확: PROCESSING -> RECONCILING (Toss 응답 유실, 나중에 조회로 확정)
    public void markReconciling() {
        if (status != PaymentAttemptStatus.PROCESSING) {
            throw new IllegalStateException(
                    "PROCESSING 상태에서만 결과 확인 중으로 바꿀 수 있습니다. status=" + status);
        }
        this.status = PaymentAttemptStatus.RECONCILING;
    }

    private void requireInProgress(String action) {
        if (!isInProgress()) {
            throw new IllegalStateException(
                    "처리 중 또는 결과 확인 중 상태에서만 " + action + "할 수 있습니다. status=" + status);
        }
    }
}
