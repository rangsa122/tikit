package com.tikit.payment.repository;

import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

    // 여러 결제 시도의 취소를 최신순으로 조회 (예매 조회의 최신 취소 상태용)
    List<Cancellation> findByPaymentAttemptIdInOrderByIdDesc(Collection<Long> paymentAttemptIds);

    // 이 결제에 해당 상태의 취소가 있는지 (진행 중 취소 확인용)
    boolean existsByPaymentAttemptIdAndStatusIn(Long paymentAttemptId, Collection<CancellationStatus> statuses);
}