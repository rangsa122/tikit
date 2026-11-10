package com.tikit.common.idempotency;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {
    // 같은 회원, 작업, 키의 기록 (행 잠금: 같은 키 재요청이 동시에 와도 하나씩 처리)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyRequest> findByMemberIdAndOperationIdAndIdempotencyKey(
            Long memberId, String operationId, String idempotencyKey);
}
