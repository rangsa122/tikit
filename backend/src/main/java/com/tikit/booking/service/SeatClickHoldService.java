package com.tikit.booking.service;

import com.tikit.booking.exception.BookingException;
import com.tikit.booking.redis.SeatLockRepository;
import com.tikit.booking.redis.SeatLockRepository.HoldResult;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.SeatStatus;
import com.tikit.performance.entity.SessionSeat;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.service.AdmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 좌석을 누르는 순간의 임시 선점
 *
 * 결제하기를 누를 때 한꺼번에 잡으면 한 좌석만 겹쳐도 전체가 실패하므로, 좌석마다 먼저 잡아 둠
 * 입장 권한이 끝나면 함께 풀리고, 예매를 만들면 BookingService가 이어받음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatClickHoldService {

    // 입장 권한 없이 시험할 때(tikit.queue.admission-required=false) 쓰는 유지 시간
    private static final Duration FALLBACK_TTL = Duration.ofMinutes(5);

    private final SeatLockRepository seatLockRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final BookingRepository bookingRepository;
    private final AdmissionService admissionService;

    public void hold(Long memberId, String admissionIdHeader, Long sessionId, Long seatId) {
        String token = validateAdmission(memberId, admissionIdHeader, sessionId);

        List<SessionSeat> seats = seatHoldRepository.findSeats(sessionId, List.of(seatId));
        if (seats.isEmpty()) {
            throw new BookingException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (seats.getFirst().getStatus() != SeatStatus.AVAILABLE) {
            throw new BookingException(ErrorCode.SEAT_UNAVAILABLE);
        }

        HoldResult result = holdSeat(seatId, token, remainingSeats(memberId, sessionId));
        if (result == HoldResult.TAKEN) {
            throw new BookingException(ErrorCode.SEAT_UNAVAILABLE);
        }
        if (result == HoldResult.LIMIT_EXCEEDED) {
            throw new BookingException(ErrorCode.BOOKING_LIMIT_EXCEEDED);
        }
        if (result == HoldResult.ADMISSION_EXPIRED) {
            throw new BookingException(ErrorCode.ADMISSION_INVALID);
        }
    }

    // 내가 잡은 좌석이 아니면 아무 일도 하지 않음
    public void release(Long memberId, String admissionIdHeader, Long sessionId, Long seatId) {
        String token = validateAdmission(memberId, admissionIdHeader, sessionId);
        try {
            seatLockRepository.unlockAll(List.of(seatId), token);
        } catch (DataAccessException e) {
            log.error("좌석 임시 선점 해제 실패: seatId={}", seatId, e);
            throw new BookingException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private String validateAdmission(Long memberId, String admissionIdHeader, Long sessionId) {
        if (admissionIdHeader == null) {
            throw new BookingException(ErrorCode.ADMISSION_INVALID);
        }
        try {
            UUID admissionId = UUID.fromString(admissionIdHeader);
            admissionService.validate(admissionId, memberId, sessionId);
            return admissionId.toString();
        } catch (IllegalArgumentException e) {
            throw new BookingException(ErrorCode.ADMISSION_INVALID);
        } catch (QueueException e) {
            throw new BookingException(e.getErrorCode());
        }
    }

    // 회원별 제한에서 이 회차에 이미 예매한 좌석을 뺀 수
    private int remainingSeats(Long memberId, Long sessionId) {
        PerformanceSession session = seatHoldRepository.findSessionWithPerformance(sessionId)
                .orElseThrow(() -> new BookingException(ErrorCode.RESOURCE_NOT_FOUND));
        long booked = bookingRepository.sumSeatCount(memberId, sessionId, BookingTransactionService.ACTIVE_STATUSES);
        return (int) Math.max(0, session.getPerformance().getBookingLimitPerMember() - booked);
    }

    private HoldResult holdSeat(Long seatId, String token, int maxSeats) {
        try {
            return seatLockRepository.hold(seatId, token, maxSeats, admissionService.isRequired(), FALLBACK_TTL);
        } catch (DataAccessException e) {
            log.error("좌석 임시 선점 실패: seatId={}", seatId, e);
            throw new BookingException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
