package com.tikit.booking.service;

import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.event.SeatHoldReleasedEvent;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.common.ErrorCode;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingHoldCancelService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // 결제 전 선점 전체 해제. 이미 만료나 취소된 예매는 현재 상태를 그대로 반환
    // 결제 승인과 만료도 예매 행을 먼저 잠그므로, 잠근 뒤 확인하면 승인 시작과 겹치지 않음
    @Transactional
    public BookingResponseDTO cancelHold(Long memberId, Long bookingId) {
        Instant now = Instant.now(clock);

        // 다른 회원 예매는 없는 예매와 같은 404
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .filter(found -> found.isOwnedBy(memberId))
                .orElseThrow(() -> new BookingException(ErrorCode.RESOURCE_NOT_FOUND));

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);

        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.CANCELED) {
            return toResponse(booking, bookingSeats, now);
        }
        if (booking.isConfirmed()) {
            throw new BookingException(ErrorCode.BOOKING_ALREADY_CONFIRMED);
        }
        if (paymentAttemptRepository.existsByBookingIdAndStatusIn(
                bookingId, BookingExpiryTxService.BLOCKING_ATTEMPT_STATUSES)) {
            throw new BookingException(ErrorCode.PAYMENT_IN_PROGRESS);
        }

        if (bookingSeats.size() != booking.getSeatCount()) {
            throw new IllegalStateException(
                    "예매 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", seats=" + bookingSeats.size());
        }

        // 일부 좌석만 풀리면 예매와 좌석 상태가 어긋나므로 전체 롤백
        List<Long> seatIds = bookingSeats.stream()
                .map(BookingSeat::getSessionSeatId)
                .toList();
        int releasedSeats = seatHoldRepository.releaseSeats(seatIds, bookingId, now);
        if (releasedSeats != seatIds.size()) {
            throw new IllegalStateException(
                    "해제된 좌석 수가 맞지 않습니다. bookingId=" + bookingId + ", released=" + releasedSeats);
        }

        booking.cancel(now);
        eventPublisher.publishEvent(new SeatHoldReleasedEvent(seatIds, booking.getAdmissionId().toString()));
        return toResponse(booking, bookingSeats, now);
    }

    private BookingResponseDTO toResponse(Booking booking, List<BookingSeat> bookingSeats, Instant now) {
        Long performanceId = seatHoldRepository.findSessionWithPerformance(booking.getSessionId())
                .map(session -> session.getPerformance().getId())
                .orElse(null);
        return BookingResponseDTO.of(booking, performanceId, bookingSeats, now);
    }
}
