package com.tikit.payment.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.response.AdminPageResponseDTO;
import com.tikit.payment.dto.response.PaymentAttemptResponseDTO;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.exception.PaymentNotFoundException;
import com.tikit.payment.repository.BookingPaymentView;
import com.tikit.payment.repository.PaymentAttemptDetailView;
import com.tikit.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int ORDER_NAME_MAX_LENGTH = 100;
    private static final String ORDER_ID_PREFIX = "TIKIT_";
    private static final List<PaymentAttemptStatus> IN_PROGRESS_STATUSES =
            List.of(PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.RECONCILING);
    private static final int ADMIN_MAX_PAGE_SIZE = 100;

    private final PaymentAttemptRepository paymentAttemptRepository;

    // 결제 준비: 본인 예매를 검증하고 READY 결제 시도를 재사용하거나 새로 만들어 반환
    @Transactional
    public PaymentPrepareResult preparePaymentAttempt(Long bookingId, Long memberId) {
        String detail = "bookingId=" + bookingId + ", memberId=" + memberId;

        // 예매 행 잠금 (본인 예매가 아니면 없는 예매와 같은 404)
        BookingPaymentView booking = paymentAttemptRepository
                .findBookingForUpdate(bookingId, memberId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, detail));

        // 토큰이 아닌 현재 DB 기준으로 회원 상태와 역할 확인
        if (!MemberStatus.ACTIVE.name().equals(booking.getMemberStatus())) {
            throw new PaymentException(ErrorCode.ACCOUNT_RESTRICTED, detail);
        }
        if (!Role.MEMBER.name().equals(booking.getMemberRole())) {
            throw new PaymentException(ErrorCode.ACCESS_DENIED, detail);
        }

        if (!BookingStatus.PENDING_PAYMENT.name().equals(booking.getStatus())) {
            throw new PaymentException(ErrorCode.INVALID_BOOKING_STATE, detail);
        }

        // 승인 결과를 모르는 결제가 있으면 새 결제를 열지 않음 (이중 결제 방지)
        if (paymentAttemptRepository.existsByBookingIdAndStatusIn(bookingId, IN_PROGRESS_STATUSES)) {
            throw new PaymentException(ErrorCode.PAYMENT_IN_PROGRESS, detail);
        }

        if (!Instant.now().isBefore(booking.getExpiresAt())) {
            throw new PaymentException(ErrorCode.BOOKING_EXPIRED, detail);
        }

        // 아직 결제하지 않은 READY 시도가 있으면 같은 주문 ID로 재사용
        Optional<PaymentAttempt> readyAttempt = paymentAttemptRepository
                .findFirstByBookingIdAndStatus(bookingId, PaymentAttemptStatus.READY);
        if (readyAttempt.isPresent()) {
            return new PaymentPrepareResult(getPaymentAttempt(readyAttempt.get().getId(), memberId), false);
        }

        // 새 결제 시도: 금액은 예매의 total_amount, 주문 ID와 PG 멱등키는 시도마다 새로 생성
        PaymentAttempt newAttempt = PaymentAttempt.create(
                bookingId,
                createOrderId(),
                booking.getTotalAmount(),
                UUID.randomUUID().toString()
        );
        paymentAttemptRepository.saveAndFlush(newAttempt);

        return new PaymentPrepareResult(getPaymentAttempt(newAttempt.getId(), memberId), true);
    }

    // 결제 처리 결과 조회 (Toss를 호출하지 않고 DB 상태만 반환)
    @Transactional(readOnly = true)
    public PaymentAttemptResponseDTO getPaymentAttempt(Long paymentAttemptId, Long memberId) {
        PaymentAttemptDetailView view = paymentAttemptRepository
                .findDetailByIdAndMemberId(paymentAttemptId, memberId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "paymentAttemptId=" + paymentAttemptId + ", memberId=" + memberId));

        OffsetDateTime now = OffsetDateTime.now(SEOUL);
        OffsetDateTime expiresAt = view.getExpiresAt().atZone(SEOUL).toOffsetDateTime();
        PaymentAttemptStatus status = PaymentAttemptStatus.valueOf(view.getStatus());
        BookingStatus bookingStatus = BookingStatus.valueOf(view.getBookingStatus());

        return PaymentAttemptResponseDTO.builder()
                .paymentAttemptId(view.getPaymentAttemptId())
                .bookingId(view.getBookingId())
                .orderId(view.getOrderId())
                .orderName(buildOrderName(view.getPerformanceTitle(), view.getSeatCount()))
                .customerKey(view.getCustomerKey())
                .amount(view.getAmount())
                .currency(view.getCurrency())
                .status(status)
                .bookingStatus(bookingStatus)
                .expiresAt(expiresAt)
                .retryAllowed(isRetryAllowed(status, bookingStatus, expiresAt, now))
                .failureCode(view.getFailureCode())
                .serverTime(now)
                .build();
    }

    // 관리자 결제 내역 (최신순, 예매 ID·상태는 선택). 항목은 회원 결제 결과 조회와 같은 형식
    @Transactional(readOnly = true)
    public AdminPageResponseDTO<PaymentAttemptResponseDTO> listAdminPaymentAttempts(
            int page, int size, Long bookingId, String status) {
        validateAdminPage(page, size);
        PaymentAttemptStatus statusFilter = parseAdminStatus(status);

        Page<PaymentAttempt> attempts = paymentAttemptRepository.searchForAdmin(
                bookingId, statusFilter, PageRequest.of(page, size));

        // 이 페이지의 예매·회원을 한 번에 조회 (결제 시도마다 조회하지 않음)
        Map<Long, Booking> bookings = attempts.isEmpty()
                ? Map.of()
                : paymentAttemptRepository.findBookingsByIds(
                        attempts.stream().map(PaymentAttempt::getBookingId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));
        Map<Long, Member> members = bookings.isEmpty()
                ? Map.of()
                : paymentAttemptRepository.findMembersByIds(
                        bookings.values().stream().map(Booking::getMemberId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        OffsetDateTime now = OffsetDateTime.now(SEOUL);
        List<PaymentAttemptResponseDTO> items = attempts.getContent().stream()
                .map(attempt -> {
                    Booking booking = bookings.get(attempt.getBookingId());
                    return toAdminResponse(attempt, booking, members.get(booking.getMemberId()), now);
                })
                .toList();

        return new AdminPageResponseDTO<>(items, page, size, attempts.getTotalElements(), attempts.getTotalPages());
    }

    // 회원 결제 결과 조회(getPaymentAttempt)와 같은 필드·같은 계산
    private PaymentAttemptResponseDTO toAdminResponse(PaymentAttempt attempt, Booking booking, Member member,
                                                      OffsetDateTime now) {
        OffsetDateTime expiresAt = booking.getExpiresAt().atZone(SEOUL).toOffsetDateTime();

        return PaymentAttemptResponseDTO.builder()
                .paymentAttemptId(attempt.getId())
                .bookingId(attempt.getBookingId())
                .orderId(attempt.getOrderId())
                .orderName(buildOrderName(booking.getPerformanceTitleSnapshot(), booking.getSeatCount().intValue()))
                .customerKey(member.getCustomerKey())
                .amount(attempt.getAmount())
                .currency(attempt.getCurrency())
                .status(attempt.getStatus())
                .bookingStatus(booking.getStatus())
                .expiresAt(expiresAt)
                .retryAllowed(isRetryAllowed(attempt.getStatus(), booking.getStatus(), expiresAt, now))
                .failureCode(attempt.getFailureCode())
                .serverTime(now)
                .build();
    }

    // page 0 이상, size 1~100 (공연 목록 API와 같은 규칙)
    private void validateAdminPage(int page, int size) {
        if (page < 0 || size < 1 || size > ADMIN_MAX_PAGE_SIZE) {
            throw new PaymentException(ErrorCode.VALIDATION_ERROR, "page=" + page + ", size=" + size);
        }
    }

    // 상태 문자열 -> enum (없으면 조건 없음, 모르는 값은 400)
    private PaymentAttemptStatus parseAdminStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PaymentAttemptStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new PaymentException(ErrorCode.VALIDATION_ERROR, "status=" + status);
        }
    }

    // 최종 실패이고, 예매가 결제 대기 중이며, 결제 기한 안일 때만 새 결제 시도 가능
    private boolean isRetryAllowed(PaymentAttemptStatus status, BookingStatus bookingStatus,
                                   OffsetDateTime expiresAt, OffsetDateTime now) {
        return status == PaymentAttemptStatus.FAILED
                && bookingStatus == BookingStatus.PENDING_PAYMENT
                && now.isBefore(expiresAt);
    }

    // 결제창 주문명: 1매면 "공연명", 2매 이상이면 "공연명 외 N매" (최대 100자)
    private String buildOrderName(String performanceTitle, Integer seatCount) {
        String suffix = (seatCount != null && seatCount > 1) ? " 외 " + (seatCount - 1) + "매" : "";
        int maxTitleLength = ORDER_NAME_MAX_LENGTH - suffix.length();

        String title = performanceTitle.length() > maxTitleLength
                ? performanceTitle.substring(0, maxTitleLength)
                : performanceTitle;

        return title + suffix;
    }

    // 주문 ID: TIKIT_ + 하이픈 없는 UUID (38자, 명세 패턴 ^[A-Za-z0-9_-]{6,64}$)
    private String createOrderId() {
        return ORDER_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}