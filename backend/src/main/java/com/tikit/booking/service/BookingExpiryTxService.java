package com.tikit.booking.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.event.SeatHoldReleasedEvent;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingExpiryTxService {

    // 돈이 나갔거나 나갔을 수 있는 결제. 이 상태의 결제가 있으면 기한이 지나도 만료하지 않음
    static final List<PaymentAttemptStatus> BLOCKING_ATTEMPT_STATUSES = List.of(
            PaymentAttemptStatus.PROCESSING,
            PaymentAttemptStatus.RECONCILING,
            PaymentAttemptStatus.APPROVED
    );

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 예매 한 건을 만료하고 좌석을 해제. 실제로 만료했으면 true
    // 결제 승인도 예매 행을 먼저 잠그므로, 잠근 뒤 다시 확인하면 승인 시작과 겹치지 않음
    @Transactional
    public boolean expire(Long bookingId, Instant now) {
        Optional<Booking> locked = bookingRepository.findByIdForUpdate(bookingId);
        if (locked.isEmpty()) {
            return false;
        }

        Booking booking = locked.get();
        if (!booking.isPendingPayment() || !booking.isExpired(now)) {
            return false;
        }
        if (paymentAttemptRepository.existsByBookingIdAndStatusIn(bookingId, BLOCKING_ATTEMPT_STATUSES)) {
            return false;
        }

        List<Long> seatIds = bookingSeatRepository.findByBookingId(bookingId).stream()
                .map(BookingSeat::getSessionSeatId)
                .toList();
        if (seatIds.size() != booking.getSeatCount()) {
            throw new IllegalStateException(
                    "예매 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", seats=" + seatIds.size());
        }

        // 일부 좌석만 풀리면 예매와 좌석 상태가 어긋나므로 전체 롤백
        int releasedSeats = seatHoldRepository.releaseSeats(seatIds, bookingId, now);
        if (releasedSeats != seatIds.size()) {
            throw new IllegalStateException(
                    "해제된 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", released=" + releasedSeats);
        }

        booking.expire();
        eventPublisher.publishEvent(new SeatHoldReleasedEvent(seatIds, booking.getAdmissionId().toString()));
        return true;
    }
}
