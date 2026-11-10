package com.tikit.common.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

// 프론트가 보낸 Idempotency-Key 저장 (회원 + 작업 + 키가 같으면 같은 요청)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "idempotency_requests")
public class IdempotencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "operation_id", nullable = false, length = 100)
    private String operationId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "resource_type", length = 40)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // 처음 들어온 요청을 처리 중으로 저장
    public static IdempotencyRequest start(Long memberId, String operationId, String idempotencyKey,
                                           String requestHash, Instant now, Duration retention) {
        IdempotencyRequest request = new IdempotencyRequest();
        request.memberId = memberId;
        request.operationId = operationId;
        request.idempotencyKey = idempotencyKey;
        request.requestHash = requestHash;
        request.status = IdempotencyStatus.PROCESSING;
        request.createdAt = now;
        request.expiresAt = now.plus(retention);
        return request;
    }

    // 같은 키로 온 요청이 처음과 같은 내용인지
    public boolean isSameRequest(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    // 처리 완료: 결과 리소스(예: PAYMENT_ATTEMPT, 10)를 기록해 재요청 때 재사용
    public void succeed(String resourceType, Long resourceId) {
        this.status = IdempotencyStatus.SUCCEEDED;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    // 처리 실패: 같은 키로 다시 처리할 수 있게 둠
    public void fail() {
        this.status = IdempotencyStatus.FAILED;
    }

    // 실패한 요청을 같은 키로 다시 처리 시작
    public void restart() {
        this.status = IdempotencyStatus.PROCESSING;
    }
}