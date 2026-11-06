package com.tikit.booking.service;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.redis.SeatLockRepository;
import com.tikit.common.ErrorCode;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.service.AdmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    // 결제 제한시간 (Redis 좌석 락 유지 시간과 같음)
    static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    private final SeatLockRepository seatLockRepository;
    private final BookingTransactionService bookingTransactionService;
    private final AdmissionService admissionService;
    private final Clock clock;

    // Redis로 좌석을 먼저 잡고, DB 선점이 실패하면 Redis 락을 풀어 줌
    public BookingResult create(Long memberId, String admissionIdHeader, BookingCreateRequestDTO request) {
        List<Long> seatIds = request.seatIds().stream().sorted().toList();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BookingException(ErrorCode.VALIDATION_ERROR);
        }

        Instant now = Instant.now(clock);
        UUID admissionId = resolveAdmissionId(admissionIdHeader);

        if (admissionIdHeader != null) {
            // 같은 입장 ID로 이미 만든 예매는 입장 권한이 만료되거나 소비됐어도 돌려줌
            Optional<BookingResponseDTO> existing =
                    bookingTransactionService.findExisting(memberId, admissionId, now);
            if (existing.isPresent()) {
                return new BookingResult(existing.get(), false);
            }
        }

        validateAdmission(admissionId, memberId, request.sessionId());

        String lockToken = admissionId.toString();
        boolean redisLocked = lockSeats(seatIds, lockToken);

        try {
            BookingResult result = bookingTransactionService.create(memberId, admissionId,
                    request.sessionId(), seatIds, now, now.plus(HOLD_DURATION));
            if (!result.created() && redisLocked) {
                // 기존 예매를 돌려준 경우 방금 잡은 락은 필요 없음
                unlockSeats(seatIds, lockToken);
            }
            if (result.created()) {
                // 좌석을 잡았으니 대기열 정원을 다음 사람에게 넘김
                admissionService.consume(admissionId);
            }
            return result;
        } catch (RuntimeException e) {
            if (redisLocked) {
                unlockSeats(seatIds, lockToken);
            }
            throw e;
        }
    }

    // Redis 좌석 락. 이미 잡힌 좌석이 있으면 409, Redis 장애면 락 없이 DB 선점만으로 진행
    private boolean lockSeats(List<Long> seatIds, String lockToken) {
        boolean locked;
        try {
            locked = seatLockRepository.lockAll(seatIds, lockToken, HOLD_DURATION);
        } catch (DataAccessException e) {
            log.warn("Redis 좌석 락 실패, DB 선점만으로 진행: seatIds={}", seatIds, e);
            return false;
        }
        if (!locked) {
            throw new BookingException(ErrorCode.SEAT_UNAVAILABLE);
        }
        return true;
    }

    // 락 해제 실패는 TTL로 풀리므로 원래 결과를 막지 않음
    private void unlockSeats(List<Long> seatIds, String lockToken) {
        try {
            seatLockRepository.unlockAll(seatIds, lockToken);
        } catch (DataAccessException e) {
            log.warn("Redis 좌석 락 해제 실패, TTL 만료로 해제됨: seatIds={}", seatIds, e);
        }
    }

    // 대기열에서 받은 입장 권한이 이 회원과 회차의 것인지 확인
    private void validateAdmission(UUID admissionId, Long memberId, Long sessionId) {
        try {
            admissionService.validate(admissionId, memberId, sessionId);
        } catch (QueueException e) {
            throw new BookingException(e.getErrorCode());
        }
    }

    // 입장 권한 검사를 끈 경우에만 헤더 없이 임시 ID를 발급
    private UUID resolveAdmissionId(String admissionIdHeader) {
        if (admissionIdHeader == null) {
            if (admissionService.isRequired()) {
                throw new BookingException(ErrorCode.ADMISSION_INVALID);
            }
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(admissionIdHeader);
        } catch (IllegalArgumentException e) {
            throw new BookingException(ErrorCode.ADMISSION_INVALID);
        }
    }
}
