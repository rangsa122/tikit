package com.tikit.payment.support;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingSeat;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.Ticket;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.performance.entity.*;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// 결제 통합 테스트용 데이터 도우미
public class PaymentTestDataHelper {

    public static final String PERFORMANCE_TITLE = "테스트 공연";
    public static final String TEST_FAILURE_CODE = "TEST_FAILURE";
    public static final String CANCELLATION_REASON = "테스트 취소 사유";

    private static final String SEAT_GRADE = "R";
    private static final String SEAT_LABEL = "A1";

    private final EntityManager entityManager;

    public PaymentTestDataHelper(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // 회원 생성 (ACTIVE 또는 SUSPENDED) -> 회원 ID
    public Long createMember(MemberStatus status, Role role) {
        Member member = Member.create("payment-" + uniqueSuffix() + "@example.com", "encodedPassword", "tester", role);

        switch (status) {
            case ACTIVE -> {
            }
            case SUSPENDED -> member.suspend();
            case WITHDRAWN -> throw new IllegalArgumentException("탈퇴 회원은 테스트 데이터로 만들지 않습니다.");
        }

        entityManager.persist(member);
        entityManager.flush();
        return member.getId();
    }

    // 예매 생성 -> 예매 ID
    public Long createBooking(Long memberId, BookingStatus status, Instant expiresAt, Long totalAmount) {
        Instant now = Instant.now();
        Instant startsAt = now.plus(30, ChronoUnit.DAYS);
        Member member = entityManager.find(Member.class, memberId);

        Genre genre = Genre.create("T" + uniqueSuffix().substring(0, 12), "테스트 장르", null, 0, true);
        entityManager.persist(genre);

        Performance performance = Performance.createManual(member, genre, PERFORMANCE_TITLE, "테스트 공연장",
                null, "테스트 설명", null, LocalDate.now(), LocalDate.now().plusDays(60), null);
        entityManager.persist(performance);

        SaleOpening opening = SaleOpening.create(performance, now.minus(1, ChronoUnit.DAYS));
        entityManager.persist(opening);

        SeatTemplate seatTemplate = SeatTemplate.create("테스트 좌석 템플릿", 1, true);
        entityManager.persist(seatTemplate);

        PerformanceSession session = PerformanceSession.create(performance, opening.getId(), seatTemplate.getId(),
                startsAt, startsAt.minus(1, ChronoUnit.HOURS), startsAt.minus(1, ChronoUnit.DAYS),
                "v1", "테스트 취소 정책");
        entityManager.persist(session);

        // 기한이 이미 지난 예매는 일단 기한 안으로 저장한 뒤 아래에서 시각을 당김 (expires_at > created_at 제약)
        boolean pastDeadline = !expiresAt.isAfter(now);
        Booking booking = Booking.create(memberId, session.getId(), UUID.randomUUID(), 1, totalAmount,
                PERFORMANCE_TITLE, startsAt, startsAt.minus(1, ChronoUnit.DAYS), "v1", "{}",
                pastDeadline ? now.plus(5, ChronoUnit.MINUTES) : expiresAt);
        changeBookingStatus(booking, status, now);
        entityManager.persist(booking);
        entityManager.flush();

        createBookingSeat(session, seatTemplate, booking, status, totalAmount);

        if (pastDeadline) {
            entityManager.createQuery("""
                            UPDATE Booking b
                            SET b.createdAt = :createdAt, b.expiresAt = :expiresAt
                            WHERE b.id = :bookingId
                            """)
                    .setParameter("createdAt", expiresAt.minus(5, ChronoUnit.MINUTES))
                    .setParameter("expiresAt", expiresAt)
                    .setParameter("bookingId", booking.getId())
                    .executeUpdate();
            entityManager.refresh(booking);
        }

        return booking.getId();
    }

    // 결제 시도 생성 (상태, 금액 지정) -> 결제 시도 ID
    public  Long createPaymentAttempt(Long bookingId, PaymentAttemptStatus status, Long amount) {
        Instant now = Instant.now();
        PaymentAttempt attempt = PaymentAttempt.create(bookingId, "TIKIT_" + uniqueSuffix(), amount,
                UUID.randomUUID().toString());

        if (status != PaymentAttemptStatus.READY) {
            attempt.startConfirmation("test-payment-key-" + uniqueSuffix(), now);
        }

        switch (status) {
            case READY, PROCESSING -> {
            }
            case APPROVED -> attempt.approve(now);
            case FAILED -> attempt.fail(TEST_FAILURE_CODE);
            case RECONCILING -> attempt.markReconciling();
        }

        entityManager.persist(attempt);
        entityManager.flush();
        return attempt.getId();
    }

    // 취소 생성 (요청자, 취소 주체, 상태 지정) -> 취소 ID
    public Long createCancellation(Long paymentAttemptId, Long requestedBy, CancellationInitiatorRole initiatorRole,
                                   CancellationStatus status, Long paidAmount) {
        Cancellation cancellation = Cancellation.create(paymentAttemptId, requestedBy, initiatorRole,
                CANCELLATION_REASON, paidAmount, 0L, "v1", UUID.randomUUID().toString());

        if (status != CancellationStatus.REQUESTED) {
            cancellation.startProcessing();
        }

        switch (status) {
            case REQUESTED, PROCESSING -> {
            }
            case SUCCEEDED -> cancellation.succeed("test-cancel-tx-" + uniqueSuffix(), Instant.now());
            case FAILED -> cancellation.fail(TEST_FAILURE_CODE);
            case RECONCILING -> cancellation.markReconciling();
        }

        entityManager.persist(cancellation);
        entityManager.flush();
        return cancellation.getId();
    }

    // 좌석 1개 생성: 등급 가격 -> 템플릿 좌석 -> 회차 좌석 -> 예매 좌석 (외래키 순서)
    // 템플릿 좌석, 회차 좌석은 엔티티 생성 메서드가 없어 SQL로 저장
    // 확정 예매는 실제 결제 승인처럼 좌석마다 티켓 1장 발급 (전체 취소 시 티켓 무효 처리 확인용)
    private void createBookingSeat(PerformanceSession session, SeatTemplate seatTemplate,
                                   Booking booking, BookingStatus status, Long price) {
        entityManager.persist(SessionGradePrice.create(session, SEAT_GRADE, price));
        entityManager.flush();

        Long templateSeatId = ((Number) entityManager.createNativeQuery("""
                        INSERT INTO template_seats (template_id, seat_code, label, row_label, seat_number, grade, x, y)
                        VALUES (:templateId, :seatCode, :label, 'A', 1, :grade, 0, 0)
                        RETURNING id
                        """)
                .setParameter("templateId", seatTemplate.getId())
                .setParameter("seatCode", SEAT_LABEL)
                .setParameter("label", SEAT_LABEL)
                .setParameter("grade", SEAT_GRADE)
                .getSingleResult()).longValue();

        // 확정 예매는 판매(SOLD), 그 외는 선점(HELD) 상태로 이 예매에 연결
        String seatStatus = status == BookingStatus.CONFIRMED ? "SOLD" : "HELD";

        Long sessionSeatId = ((Number) entityManager.createNativeQuery("""
                        INSERT INTO session_seats (session_id, seat_template_id, template_seat_id, grade, status, current_booking_id)
                        VALUES (:sessionId, :seatTemplateId, :templateSeatId, :grade, :status, :bookingId)
                        RETURNING id
                        """)
                .setParameter("sessionId", session.getId())
                .setParameter("seatTemplateId", seatTemplate.getId())
                .setParameter("templateSeatId", templateSeatId)
                .setParameter("grade", SEAT_GRADE)
                .setParameter("status", seatStatus)
                .setParameter("bookingId", booking.getId())
                .getSingleResult()).longValue();

        BookingSeat bookingSeat = BookingSeat.create(booking.getId(), session.getId(), sessionSeatId,
                SEAT_LABEL, SEAT_GRADE, price);
        entityManager.persist(bookingSeat);
        entityManager.flush();

        if (status == BookingStatus.CONFIRMED) {
            entityManager.persist(Ticket.issue(bookingSeat.getId(), Instant.now()));
            entityManager.flush();
        }
    }

    // 예매 상태를 엔티티 메서드로 변경 (PENDING_PAYMENT면 그대로)
    private void changeBookingStatus(Booking booking, BookingStatus status, Instant now) {
        switch (status) {
            case PENDING_PAYMENT -> {
            }
            case CONFIRMED -> booking.confirm(now);
            case EXPIRED -> booking.expire();
            case CANCELED -> booking.cancel(now);
        }
    }

    // UNIQUE 컬럼 충돌 방지용 랜덤 값 (하이픈 없는 소문자 UUID 32자)
    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-","");
    }
}