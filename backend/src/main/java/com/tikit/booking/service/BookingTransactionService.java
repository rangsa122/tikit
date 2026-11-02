package com.tikit.booking.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.SalesStatus;
import com.tikit.performance.entity.SeatStatus;
import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.SessionSeat;
import com.tikit.performance.entity.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingTransactionService {

    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);

    private final MemberRepository memberRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatHoldRepository seatHoldRepository;

    @Transactional(readOnly = true)
    public Optional<BookingResponseDTO> findExisting(Long memberId, UUID admissionId, Instant now) {
        return bookingRepository.findByAdmissionIdAndMemberId(admissionId, memberId)
                .map(booking -> toResponse(booking, now));
    }

    // 좌석 전부를 선점하고 임시 예매 생성. 하나라도 실패하면 전부 롤백
    @Transactional
    public BookingResult create(Long memberId, UUID admissionId, Long sessionId,
                                List<Long> seatIds, Instant now, Instant expiresAt) {
        lockActiveMember(memberId);

        // 같은 입장 ID 요청이 먼저 끝났으면 그 예매를 반환
        Optional<Booking> existing = bookingRepository.findByAdmissionIdAndMemberId(admissionId, memberId);
        if (existing.isPresent()) {
            return new BookingResult(toResponse(existing.get(), now), false);
        }

        PerformanceSession session = seatHoldRepository.findSessionWithPerformance(sessionId)
                .orElseThrow(() -> new BookingException(ErrorCode.RESOURCE_NOT_FOUND));
        Performance performance = session.getPerformance();
        validateOnSale(performance, session, now);

        List<SessionSeat> seats = seatHoldRepository.findSeats(sessionId, seatIds);
        if (seats.size() != seatIds.size()
                || seats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.AVAILABLE)) {
            throw new BookingException(ErrorCode.SEAT_UNAVAILABLE);
        }

        long heldCount = bookingRepository.sumSeatCount(memberId, sessionId, ACTIVE_STATUSES);
        if (heldCount + seatIds.size() > performance.getBookingLimitPerMember()) {
            throw new BookingException(ErrorCode.BOOKING_LIMIT_EXCEEDED);
        }

        Map<String, Long> priceByGrade = seatHoldRepository.findGradePrices(sessionId).stream()
                .collect(Collectors.toMap(price -> price.getId().getGrade(), SessionGradePrice::getPrice));
        long totalAmount = 0;
        for (SessionSeat seat : seats) {
            Long price = priceByGrade.get(seat.getGrade());
            if (price == null) {
                throw new BookingException(ErrorCode.INVALID_SALES_CONFIGURATION);
            }
            totalAmount += price;
        }

        Booking booking = bookingRepository.saveAndFlush(Booking.create(
                memberId,
                sessionId,
                admissionId,
                seats.size(),
                totalAmount,
                performance.getTitle(),
                session.getStartsAt(),
                session.getCancellationDeadline(),
                session.getPolicyVersion(),
                policySnapshot(session),
                expiresAt
        ));

        // 조회 이후 다른 요청이 먼저 잡은 좌석이 있으면 전체 롤백
        int heldSeats = seatHoldRepository.holdSeats(sessionId, seatIds, booking.getId(), now);
        if (heldSeats != seatIds.size()) {
            throw new BookingException(ErrorCode.SEAT_UNAVAILABLE);
        }

        List<BookingSeat> bookingSeats = bookingSeatRepository.saveAll(seats.stream()
                .map(seat -> BookingSeat.create(
                        booking.getId(),
                        sessionId,
                        seat.getId(),
                        seat.getTemplateSeat().getLabel(),
                        seat.getGrade(),
                        priceByGrade.get(seat.getGrade())))
                .toList());

        return new BookingResult(
                BookingResponseDTO.of(booking, performance.getId(), bookingSeats, now), true);
    }

    private void lockActiveMember(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BookingException(ErrorCode.ACCESS_DENIED));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BookingException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        if (member.getRole() != Role.MEMBER) {
            throw new BookingException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateOnSale(Performance performance, PerformanceSession session, Instant now) {
        if (performance.getPublicationStatus() != PublicationStatus.PUBLISHED
                || performance.getVisibility() != Visibility.PUBLIC) {
            throw new BookingException(ErrorCode.PERFORMANCE_NOT_PUBLISHED);
        }
        if (performance.getSalesStatus() != SalesStatus.ENABLED) {
            throw new BookingException(ErrorCode.SALE_STOPPED);
        }
        Instant opensAt = seatHoldRepository.findOpensAt(session.getOpeningId())
                .orElseThrow(() -> new BookingException(ErrorCode.SALE_NOT_OPEN));
        if (now.isBefore(opensAt) || !now.isBefore(session.getSalesCloseAt())) {
            throw new BookingException(ErrorCode.SALE_NOT_OPEN);
        }
    }

    private BookingResponseDTO toResponse(Booking booking, Instant now) {
        Long performanceId = seatHoldRepository.findSessionWithPerformance(booking.getSessionId())
                .map(session -> session.getPerformance().getId())
                .orElse(null);
        return BookingResponseDTO.of(booking, performanceId,
                bookingSeatRepository.findByBookingId(booking.getId()), now);
    }

    // 예매 시점 취소 정책을 JSON으로 보관
    private String policySnapshot(PerformanceSession session) {
        return "{\"policyVersion\":" + jsonString(session.getPolicyVersion())
                + ",\"cancellationPolicy\":" + jsonString(session.getCancellationPolicy()) + "}";
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
