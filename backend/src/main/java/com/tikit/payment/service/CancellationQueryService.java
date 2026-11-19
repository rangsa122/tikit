package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.response.AdminPageResponseDTO;
import com.tikit.payment.dto.response.CancellationResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 예매 전체 취소 결과 조회 (DB의 현재 상태로 응답)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CancellationQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int ADMIN_MAX_PAGE_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final CancellationRepository cancellationRepository;
    private final Clock clock;

    // 회원 본인의 취소 결과 조회 (없는 취소, 다른 회원의 취소는 모두 404)
    public CancellationResponseDTO getOwnedCancellation(Long cancellationId, Long memberId) {
        String detail = "cancellationId=" + cancellationId + ", memberId=" + memberId;

        Long bookingId = cancellationRepository.findOwnedBookingId(cancellationId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return getCancellation(cancellationId, bookingId, memberId);
    }

    // 회원 기준 응답 (재취소 가능 여부에 취소 기한 적용)
    public CancellationResponseDTO getCancellation(Long cancellationId, Long bookingId, Long memberId) {
        String detail = "cancellationId=" + cancellationId + ", bookingId=" + bookingId + ", memberId=" + memberId;
        Instant now = Instant.now(clock);

        Booking booking = bookingRepository.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        Cancellation cancellation = cancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return toResponse(cancellation, booking, isRetryAllowed(cancellation, booking, now, true), now);
    }

    // 관리자 기준 응답 (주인 상관없이 조회, 재취소 가능 여부에 취소 기한 미적용)
    public CancellationResponseDTO getAdminCancellation(Long cancellationId) {
        String detail = "cancellationId=" + cancellationId + ", admin";
        Instant now = Instant.now(clock);

        Long bookingId = cancellationRepository.findBookingIdById(cancellationId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));
        Cancellation cancellation = cancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        return toResponse(cancellation, booking, isRetryAllowed(cancellation, booking, now, false), now);
    }

    // 관리자 취소·환불 내역 (최신순, 예매 ID·상태는 선택). 항목은 관리자 기준 응답과 같은 형식
    public AdminPageResponseDTO<CancellationResponseDTO> listAdminCancellations(
            int page, int size, Long bookingId, String status) {
        validateAdminPage(page, size);
        CancellationStatus statusFilter = parseAdminStatus(status);
        Instant now = Instant.now(clock);

        Page<Cancellation> cancellations = cancellationRepository.searchForAdmin(
                bookingId, statusFilter, PageRequest.of(page, size));

        // 이 페이지의 취소 -> 결제 시도 -> 예매를 한 번에 연결 (취소마다 조회하지 않음)
        Map<Long, Long> bookingIdByAttempt = cancellations.isEmpty()
                ? Map.of()
                : cancellationRepository.findPaymentAttemptsByIds(
                        cancellations.stream().map(Cancellation::getPaymentAttemptId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PaymentAttempt::getId, PaymentAttempt::getBookingId));
        Map<Long, Booking> bookings = bookingIdByAttempt.isEmpty()
                ? Map.of()
                : bookingRepository.findAllById(bookingIdByAttempt.values().stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));

        List<CancellationResponseDTO> items = cancellations.getContent().stream()
                .map(cancellation -> {
                    Booking booking = bookings.get(bookingIdByAttempt.get(cancellation.getPaymentAttemptId()));
                    return toResponse(cancellation, booking, isRetryAllowed(cancellation, booking, now, false), now);
                })
                .toList();

        return new AdminPageResponseDTO<>(items, page, size,
                cancellations.getTotalElements(), cancellations.getTotalPages());
    }

    private CancellationResponseDTO toResponse(Cancellation cancellation, Booking booking,
                                               boolean retryAllowed, Instant now) {
        return CancellationResponseDTO.builder()
                .cancellationId(cancellation.getId())
                .bookingId(booking.getId())
                .status(cancellation.getStatus())
                .refundAmount(cancellation.getRefundAmount())
                .feeAmount(cancellation.getFeeAmount())
                .reason(cancellation.getReason())
                .initiatorRole(cancellation.getInitiatorRole())
                .retryAllowed(retryAllowed)
                .failureCode(cancellation.getFailureCode())
                .createdAt(toSeoul(cancellation.getCreatedAt()))
                .completedAt(toSeoul(cancellation.getCompletedAt()))
                .serverTime(toSeoul(now))
                .build();
    }

    // 실패로 끝났고 예매가 아직 확정이면 다시 취소 가능 (회원은 예매 당시 정책의 기한 안이어야 함)
    private boolean isRetryAllowed(Cancellation cancellation, Booking booking, Instant now, boolean checkDeadline) {
        if (cancellation.getStatus() != CancellationStatus.FAILED || booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }
        if (!checkDeadline) {
            return true;
        }
        CancellationPolicy policy = CancellationPolicy.from(booking.getPolicyVersionSnapshot());
        return policy.isCancellable(now, booking.getCancellationDeadlineSnapshot());
    }

    // page 0 이상, size 1~100 (결제 내역과 같은 규칙)
    private void validateAdminPage(int page, int size) {
        if (page < 0 || size < 1 || size > ADMIN_MAX_PAGE_SIZE) {
            throw new PaymentException(ErrorCode.VALIDATION_ERROR, "page=" + page + ", size=" + size);
        }
    }

    // 상태 문자열 -> enum (없으면 조건 없음, 모르는 값은 400)
    private CancellationStatus parseAdminStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CancellationStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new PaymentException(ErrorCode.VALIDATION_ERROR, "status=" + status);
        }
    }

    private OffsetDateTime toSeoul(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}