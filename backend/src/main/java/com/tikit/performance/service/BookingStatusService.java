package com.tikit.performance.service;

import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.response.BookingStatusResponseDTO;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingStatusService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PerformanceAccessChecker accessChecker;
    private final PerformanceRepository performanceRepository;
    private final PerformanceSessionRepository performanceSessionRepository;
    private final SessionSeatRepository sessionSeatRepository;
    private final BookingRepository bookingRepository;

    public BookingStatusResponseDTO getBookingStatus(Long memberId, Long performanceId) {
        // 조회는 MANAGER와 ADMIN 모두 가능
        accessChecker.requireViewer(memberId);

        if (!performanceRepository.existsById(performanceId)) {
            throw new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<PerformanceSession> sessions =
                performanceSessionRepository.findByPerformance(performanceId);

        if (sessions.isEmpty()) {
            return new BookingStatusResponseDTO(
                    performanceId,
                    new BookingStatusResponseDTO.Summary(0, 0, 0, 0, 0, 0),
                    List.of()
            );
        }

        List<Long> sessionIds = sessions.stream()
                .map(PerformanceSession::getId)
                .toList();

        // 회차가 몇 개든 쿼리는 두 번
        Map<Long, SessionSeatRepository.SeatCount> seats =
                sessionSeatRepository.countBySessions(sessionIds).stream()
                        .collect(Collectors.toMap(
                                SessionSeatRepository.SeatCount::getSessionId,
                                Function.identity()));

        Map<Long, BookingRepository.BookingCount> bookings =
                bookingRepository.countBySessions(sessionIds).stream()
                        .collect(Collectors.toMap(
                                BookingRepository.BookingCount::getSessionId,
                                Function.identity()));

        List<BookingStatusResponseDTO.SessionStatus> rows = new ArrayList<>();

        long totalSeats = 0, soldSeats = 0, heldSeats = 0;
        long bookingCount = 0, canceledCount = 0, revenue = 0;

        for (PerformanceSession session : sessions) {
            var seat = seats.get(session.getId());
            var booking = bookings.get(session.getId());

            // 좌석이나 예매가 아직 없는 회차는 집계 결과가 없다
            long sessionTotal = seat == null ? 0 : seat.getTotalSeats();
            long sessionSold = seat == null ? 0 : seat.getSoldSeats();
            long sessionHeld = seat == null ? 0 : seat.getHeldSeats();
            long sessionBookings = booking == null ? 0 : booking.getBookingCount();
            long sessionCanceled = booking == null ? 0 : booking.getCanceledCount();
            long sessionRevenue = booking == null ? 0 : booking.getRevenue();

            rows.add(new BookingStatusResponseDTO.SessionStatus(
                    session.getId(),
                    session.getStartsAt().atZone(KST).toOffsetDateTime(),
                    sessionTotal, sessionSold, sessionHeld,
                    sessionBookings, sessionCanceled, sessionRevenue
            ));

            totalSeats += sessionTotal;
            soldSeats += sessionSold;
            heldSeats += sessionHeld;
            bookingCount += sessionBookings;
            canceledCount += sessionCanceled;
            revenue += sessionRevenue;
        }

        return new BookingStatusResponseDTO(
                performanceId,
                new BookingStatusResponseDTO.Summary(
                        totalSeats, soldSeats, heldSeats,
                        bookingCount, canceledCount, revenue
                ),
                rows
        );
    }
}