package com.tikit.booking.service;

import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.dto.response.TicketListResponseDTO;
import com.tikit.booking.dto.response.TicketListResponseDTO.TicketItem;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.Ticket;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.booking.repository.TicketRepository;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.PageResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import com.tikit.performance.entity.PerformanceSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingQueryService {

    // 최신 생성순, 동률은 bookingId 내림차순
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final TicketRepository ticketRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationRepository cancellationRepository;
    private final Clock clock;

    public BookingResponseDTO getBooking(Long memberId, Long bookingId) {
        return toResponses(List.of(findOwned(memberId, bookingId))).getFirst();
    }

    // status가 없으면 전체 상태
    public PageResponseDTO<BookingResponseDTO> getMyBookings(Long memberId, BookingStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Booking> bookings = status == null
                ? bookingRepository.findByMemberId(memberId, pageable)
                : bookingRepository.findByMemberIdAndStatus(memberId, status, pageable);

        return PageResponseDTO.from(
                new PageImpl<>(toResponses(bookings.getContent()), pageable, bookings.getTotalElements()));
    }

    // 티켓은 예매 확정 때 발급되므로 미확정 예매는 빈 배열
    public TicketListResponseDTO getTickets(Long memberId, Long bookingId) {
        Booking booking = findOwned(memberId, bookingId);

        List<BookingSeat> seats = bookingSeatRepository.findByBookingIdInOrderById(List.of(bookingId));
        List<Long> seatIds = seats.stream().map(BookingSeat::getId).toList();
        Map<Long, Ticket> ticketBySeatId = ticketRepository.findByBookingSeatIds(seatIds).stream()
                .collect(Collectors.toMap(Ticket::getBookingSeatId, Function.identity()));

        List<TicketItem> items = seats.stream()
                .filter(seat -> ticketBySeatId.containsKey(seat.getId()))
                .map(seat -> TicketItem.of(ticketBySeatId.get(seat.getId()), seat, booking))
                .toList();
        return new TicketListResponseDTO(items);
    }

    // 다른 회원 예매는 없는 예매와 같은 404
    private Booking findOwned(Long memberId, Long bookingId) {
        return bookingRepository.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new BookingException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 예매마다 조회하지 않도록 좌석, 공연, 결제, 취소를 한 번에 읽어서 조립
    private List<BookingResponseDTO> toResponses(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        List<Long> sessionIds = bookings.stream().map(Booking::getSessionId).distinct().toList();

        Map<Long, List<BookingSeat>> seatsByBookingId = bookingSeatRepository.findByBookingIdInOrderById(bookingIds)
                .stream()
                .collect(Collectors.groupingBy(BookingSeat::getBookingId));
        Map<Long, Long> performanceIdBySessionId = seatHoldRepository.findSessionsWithPerformance(sessionIds).stream()
                .collect(Collectors.toMap(PerformanceSession::getId, session -> session.getPerformance().getId()));

        // 최신순으로 받으므로 먼저 나온 항목이 최신
        Map<Long, PaymentAttempt> attemptByBookingId = paymentAttemptRepository.findByBookingIdInOrderByIdDesc(bookingIds)
                .stream()
                .collect(Collectors.toMap(PaymentAttempt::getBookingId, Function.identity(), (latest, older) -> latest));
        List<Long> attemptIds = attemptByBookingId.values().stream().map(PaymentAttempt::getId).toList();
        Map<Long, Cancellation> cancellationByAttemptId = cancellationRepository
                .findByPaymentAttemptIdInOrderByIdDesc(attemptIds)
                .stream()
                .collect(Collectors.toMap(Cancellation::getPaymentAttemptId, Function.identity(), (latest, older) -> latest));

        return bookings.stream()
                .map(booking -> {
                    PaymentAttempt attempt = attemptByBookingId.get(booking.getId());
                    return BookingResponseDTO.of(
                            booking,
                            performanceIdBySessionId.get(booking.getSessionId()),
                            seatsByBookingId.getOrDefault(booking.getId(), List.of()),
                            attempt,
                            attempt == null ? null : cancellationByAttemptId.get(attempt.getId()),
                            now);
                })
                .toList();
    }
}
