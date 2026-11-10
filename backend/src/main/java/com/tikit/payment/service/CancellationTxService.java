package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.request.CancellationCreateRequestDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 예매 전체 취소의 트랜잭션 단위 처리 (Toss 호출은 이 클래스 밖에서)
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationTxService {

    // 진행 중 취소 (견적과 같은 기준: 환불됐을 수 있는 RECONCILING 포함, FAILED는 재시도 가능하게 제외)
    private static final List<CancellationStatus> IN_PROGRESS =
            List.of(CancellationStatus.REQUESTED, CancellationStatus.PROCESSING, CancellationStatus.RECONCILING);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationRepository cancellationRepository;
    private final Clock clock;

    // 1차: 예매, 결제 잠금 -> 취소 가능, 견적 재검증 -> 취소 요청 저장(처리 중) -> 커밋
    @Transactional
    public CancellationStart startCancellation(Long bookingId, Long memberId, CancellationCreateRequestDTO request) {
        String detail = "bookingId=" + bookingId + ", memberId=" + memberId;
        Instant now = Instant.now(clock);

        // 1. 예매 잠금 (같은 예매 취소가 동시에 와도 하나씩 처리, 다른 회원 예매는 404)
        Booking booking = paymentAttemptRepository.findOwnedBookingForUpdate(bookingId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        // 2. 확정 예매만 취소 가능 (결제 전 예매는 선점 해제 API, 이미 취소면 불가)
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new PaymentException(ErrorCode.CANCELLATION_NOT_ALLOWED, detail + ", status=" + booking.getStatus());
        }

        // 3. 승인된 결제 잠금 (확정 예매인데 없으면 데이터 불일치)
        PaymentAttempt approved = lockApprovedAttempt(bookingId, detail);

        // 4. 진행 중 취소가 있으면 거절(이중 환불 방지)
        if (cancellationRepository.existsByPaymentAttemptIdAndStatusIn(approved.getId(), IN_PROGRESS)) {
            throw new PaymentException(ErrorCode.CANCELLATION_IN_PROGRESS, detail);
        }

        // 5. 예매 당시 정책으로 기한 확인
        CancellationPolicy policy = CancellationPolicy.from(booking.getPolicyVersionSnapshot());
        if (!policy.isCancellable(now, booking.getCancellationDeadlineSnapshot())) {
            throw new PaymentException(ErrorCode.CANCELLATION_NOT_ALLOWED, detail + ", deadline passed");
        }

        // 6. 서버 재계산 금액·정책이 화면에서 본 견적과 다르면 실행하지 않음
        Long paidAmount = approved.getAmount();
        Long feeAmount = policy.calculateFee(paidAmount);
        Long refundAmount = paidAmount - feeAmount;
        if (!refundAmount.equals(request.getExpectedRefundAmount())
                || !policy.getVersion().equals(request.getPolicyVersion())) {
            throw new PaymentException(ErrorCode.CANCELLATION_QUOTE_CHANGED, detail
                    + ", expected=" + request.getExpectedRefundAmount() + "/" + request.getPolicyVersion()
                    + ", actual=" + refundAmount + "/" + policy.getVersion());
        }

        // 7. 취소 요청 저장 -> 처리 중 (Toss 멱등키는 이 취소 건에 고정, 재시도·복구 때 같은 키 재사용)
        Cancellation cancellation = Cancellation.create(approved.getId(), memberId, CancellationInitiatorRole.MEMBER,
                request.getReason(), paidAmount, feeAmount, policy.getVersion(), UUID.randomUUID().toString());
        cancellation.startProcessing();
        cancellationRepository.save(cancellation);

        log.info("cancellation started: {}, cancellationId={}, refundAmount={}", detail, cancellation.getId(), refundAmount);

        return new CancellationStart(cancellation.getId(), bookingId, approved.getPaymentKey(),
                request.getReason(), refundAmount, cancellation.getPgIdempotencyKey());
    }

    private PaymentAttempt lockApprovedAttempt(Long bookingId, String detail) {
        PaymentAttempt approved = paymentAttemptRepository
                .findFirstByBookingIdAndStatus(bookingId, PaymentAttemptStatus.APPROVED)
                .orElseThrow(() -> new IllegalStateException("확정 예매에 승인된 결제가 없습니다. " + detail));
        return paymentAttemptRepository.findByIdForUpdate(approved.getId())
                .orElseThrow(() -> new IllegalStateException("승인된 결제를 잠글 수 없습니다. " + detail));
    }
}
