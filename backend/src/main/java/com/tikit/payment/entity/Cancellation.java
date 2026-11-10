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
@Table(name = "cancellations")
public class Cancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_attempt_id", nullable = false)
    private Long paymentAttemptId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_role", nullable = false, length = 20)
    private CancellationInitiatorRole initiatorRole;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CancellationStatus status = CancellationStatus.REQUESTED;

    @Column(name = "paid_amount", nullable = false)
    private Long paidAmount;

    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "policy_version", nullable = false, length = 30)
    private String policyVersion;

    @Column(name = "pg_idempotency_key", nullable = false, unique = true, length = 128)
    private String pgIdempotencyKey;

    @Column(name = "pg_transaction_key", unique = true, length = 200)
    private String pgTransactionKey;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Cancellation create(Long paymentAttemptId, Long requestedBy, CancellationInitiatorRole initiatorRole, String reason, Long paidAmount, Long feeAmount, String policyVersion, String pgIdempotencyKey) {

        if (feeAmount < 0 || feeAmount > paidAmount) {
            throw new IllegalArgumentException("수수료는 0 이상, 결제 금액 이하여야 합니다.");
        }

        Cancellation cancellation = new Cancellation();
        cancellation.paymentAttemptId = paymentAttemptId;
        cancellation.requestedBy = requestedBy;
        cancellation.initiatorRole = initiatorRole;
        cancellation.reason = reason;
        cancellation.paidAmount = paidAmount;
        cancellation.feeAmount = feeAmount;
        cancellation.refundAmount = paidAmount - feeAmount;
        cancellation.policyVersion = policyVersion;
        cancellation.pgIdempotencyKey = pgIdempotencyKey;

        return cancellation;
    }

    // Toss 취소 요청 직전: 접수 -> 처리 중 (1차 트랜잭션에서 커밋)
    public void startProcessing() {
        if (status != CancellationStatus.REQUESTED) {
            throw new IllegalStateException(
                    "REQUESTED 상태에서만 취소 처리를 시작할 수 있습니다. status=" + status);
        }
        this.status = CancellationStatus.PROCESSING;
    }

    // 환불 성공: Toss 취소 거래 키와 완료 시각 저장 (처리 중 또는 결과 확인 중에서만)
    public void succeed(String pgTransactionKey, Instant completedAt) {
        requireInProgress("환불 성공 처리");
        if (pgTransactionKey == null || pgTransactionKey.isBlank()) {
            throw new IllegalArgumentException("환불 성공에는 Toss 취소 거래 키가 필요합니다.");
        }
        this.pgTransactionKey = pgTransactionKey;
        this.completedAt = completedAt;
        this.status = CancellationStatus.SUCCEEDED;
    }

    // 환불 실패: Toss가 거절 (처리 중 또는 결과 확인 중에서만)
    public void fail(String failureCode) {
        requireInProgress("환불 실패 처리");
        this.failureCode = failureCode;
        this.status = CancellationStatus.FAILED;
    }

    // 응답 유실·Toss 장애: 환불됐는지 모름 -> 결과 확인 중 (처리 중에서만)
    public void markReconciling() {
        if (status != CancellationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "PROCESSING 상태에서만 결과 확인 중으로 바꿀 수 있습니다. status=" + status);
        }
        this.status = CancellationStatus.RECONCILING;
    }

    private void requireInProgress(String action) {
        if (status != CancellationStatus.PROCESSING && status != CancellationStatus.RECONCILING) {
            throw new IllegalStateException(
                    action + "는 PROCESSING 또는 RECONCILING 상태에서만 가능합니다. status=" + status);
        }
    }
}
