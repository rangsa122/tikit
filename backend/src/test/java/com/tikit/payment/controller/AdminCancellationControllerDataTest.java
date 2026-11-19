package com.tikit.payment.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.common.ErrorCode;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.support.PaymentTestDataHelper;
import com.tikit.performance.entity.SeatStatus;
import com.tikit.util.JWTProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 관리자 전체 환불 API 예매 데이터 통합 테스트 (Toss만 가짜, 테스트마다 롤백)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class AdminCancellationControllerDataTest {

    private static final Long AMOUNT = 55000L;
    private static final String TRANSACTION_KEY = "test-admin-cancel-transaction-key";
    private static final String REASON = "공연 취소에 따른 운영 환불";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @PersistenceContext
    private EntityManager entityManager;

    private PaymentTestDataHelper dataHelper;

    @BeforeEach
    void setup() {
        dataHelper = new PaymentTestDataHelper(entityManager);
    }

    @Test
    @DisplayName("관리자 견적: 취소 기한이 지난 확정 예매도 eligible, 환불액 = 결제액 (회원 견적은 기한 지남)")
    void quote_afterDeadline_eligible() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);
        passCancellationDeadline(bookingId);

        // when
        ResultActions adminResult = mvc.perform(get("/api/v1/admin/bookings/" + bookingId + "/cancellation-quote")
                .header("Authorization", "Bearer " + accessTokenFor(adminId)));
        ResultActions memberResult = mvc.perform(get("/api/v1/bookings/" + bookingId + "/cancellation-quote")
                .header("Authorization", "Bearer " + accessTokenFor(ownerId)));

        System.out.println("테스트: " + adminResult.andReturn().getResponse().getContentAsString());
        System.out.println("테스트: " + memberResult.andReturn().getResponse().getContentAsString());

        // then
        assertThat(adminResult.andReturn().getResponse().getStatus()).isEqualTo(200);
        adminResult.andExpect(jsonPath("$.data.eligible").value(true));
        adminResult.andExpect(jsonPath("$.data.refundAmount").value(AMOUNT));
        adminResult.andExpect(jsonPath("$.data.feeAmount").value(0));

        memberResult.andExpect(jsonPath("$.data.eligible").value(false));
        memberResult.andExpect(jsonPath("$.data.reasonCode").value("CANCELLATION_DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("관리자 환불: 다른 회원의 예매를 환불하면 202 SUCCEEDED(ADMIN), 예매 CANCELED·좌석 AVAILABLE·티켓 VOID")
    void cancel_otherMemberBooking_succeeded() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));

        // when
        ResultActions resultActions = mvc.perform(adminCancelRequest(bookingId, adminId, newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(202);
        resultActions.andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        resultActions.andExpect(jsonPath("$.data.initiatorRole").value("ADMIN"));
        resultActions.andExpect(jsonPath("$.data.refundAmount").value(AMOUNT));

        Cancellation cancellation = latestCancellation(bookingId);
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
        assertThat(cancellation.getInitiatorRole()).isEqualTo(CancellationInitiatorRole.ADMIN);
        assertThat(cancellation.getRequestedBy()).isEqualTo(adminId);
        assertThat(cancellation.getPgTransactionKey()).isEqualTo(TRANSACTION_KEY);

        // 관리자가 요청해도 예매 주인의 예매가 취소됨 (환불 반영은 예매 주인 기준)
        Booking booking = entityManager.find(Booking.class, bookingId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(seatStatuses(bookingId)).containsExactly(SeatStatus.AVAILABLE);
        assertThat(ticketStatuses(bookingId)).containsExactly(TicketStatus.VOID);
    }

    @Test
    @DisplayName("취소 기한이 지난 예매: 회원 취소는 422, 관리자 환불은 202")
    void cancel_afterDeadline_adminOnly() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);
        passCancellationDeadline(bookingId);
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));

        // when
        ResultActions memberResult = mvc.perform(post("/api/v1/bookings/" + bookingId + "/cancellations")
                .header("Authorization", "Bearer " + accessTokenFor(ownerId))
                .header("Idempotency-Key", newIdempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(REASON, AMOUNT, "v1")));
        ResultActions adminResult = mvc.perform(adminCancelRequest(bookingId, adminId, newIdempotencyKey()));

        System.out.println("테스트: " + memberResult.andReturn().getResponse().getContentAsString());
        System.out.println("테스트: " + adminResult.andReturn().getResponse().getContentAsString());

        // then
        assertThat(memberResult.andReturn().getResponse().getStatus())
                .isEqualTo(ErrorCode.CANCELLATION_NOT_ALLOWED.getStatus().value());
        assertThat(adminResult.andReturn().getResponse().getStatus()).isEqualTo(202);
        adminResult.andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        assertThat(entityManager.find(Booking.class, bookingId).getStatus()).isEqualTo(BookingStatus.CANCELED);
        then(tossPaymentClient).should(times(1)).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("진행 중인 취소가 있으면 관리자도 409 CANCELLATION_IN_PROGRESS, Toss 미호출")
    void cancel_inProgress_conflict() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(ownerId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        dataHelper.createCancellation(paymentAttemptId, ownerId, CancellationInitiatorRole.MEMBER,
                CancellationStatus.RECONCILING, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(adminCancelRequest(bookingId, adminId, newIdempotencyKey()));

        System.out.println("테스트: " + resultActions.andReturn().getResponse().getContentAsString());

        // then
        ErrorCode errorCode = ErrorCode.CANCELLATION_IN_PROGRESS;
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        assertThat(cancellationCount(bookingId)).isEqualTo(1L);
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("같은 멱등키로 다시 요청하면 200과 기존 결과, Toss는 1번만 호출")
    void cancel_sameKey_replay() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));
        String idempotencyKey = newIdempotencyKey();

        // when
        ResultActions first = mvc.perform(adminCancelRequest(bookingId, adminId, idempotencyKey));
        ResultActions second = mvc.perform(adminCancelRequest(bookingId, adminId, idempotencyKey));

        System.out.println("테스트: " + first.andReturn().getResponse().getContentAsString());
        System.out.println("테스트: " + second.andReturn().getResponse().getContentAsString());

        // then
        assertThat(first.andReturn().getResponse().getStatus()).isEqualTo(202);
        assertThat(second.andReturn().getResponse().getStatus()).isEqualTo(200);
        second.andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        second.andExpect(jsonPath("$.data.initiatorRole").value("ADMIN"));

        assertThat(cancellationCount(bookingId)).isEqualTo(1L);
        then(tossPaymentClient).should(times(1)).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("MEMBER 토큰으로 관리자 환불 API를 부르면 403, Toss 미호출, 취소 없음")
    void cancel_memberToken_forbidden() throws Exception {
        // given
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);

        // when
        ResultActions resultActions = mvc.perform(adminCancelRequest(bookingId, ownerId, newIdempotencyKey()));

        System.out.println("테스트: " + resultActions.andReturn().getResponse().getContentAsString());

        // then
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(cancellationCount(bookingId)).isZero();
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    // 기한 전 확정 예매 + 승인된 결제 (좌석 SOLD, 티켓 1장)
    private Long confirmedBooking(Long memberId) {
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        return bookingId;
    }

    // 예매의 취소 기한을 과거로 (회원은 취소 불가, 관리자는 가능한 상황)
    private void passCancellationDeadline(Long bookingId) {
        entityManager.createQuery("""
                        UPDATE Booking b
                        SET b.cancellationDeadlineSnapshot = :deadline
                        WHERE b.id = :bookingId
                        """)
                .setParameter("deadline", Instant.now().minus(1, ChronoUnit.HOURS))
                .setParameter("bookingId", bookingId)
                .executeUpdate();
        entityManager.clear();
    }

    private RequestBuilder adminCancelRequest(Long bookingId, Long accountId, String idempotencyKey) {
        return post("/api/v1/admin/bookings/" + bookingId + "/cancellations")
                .header("Authorization", "Bearer " + accessTokenFor(accountId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(REASON, AMOUNT, "v1"));
    }

    private String requestBody(String reason, Long expectedRefundAmount, String policyVersion) {
        return """
                {
                  "reason": "%s",
                  "expectedRefundAmount": %d,
                  "policyVersion": "%s"
                }
                """.formatted(reason, expectedRefundAmount, policyVersion);
    }

    // 테스트마다 겹치지 않는 멱등키
    private String newIdempotencyKey() {
        return "admin-cancel-" + UUID.randomUUID();
    }

    private Cancellation latestCancellation(Long bookingId) {
        return entityManager.createQuery("""
                        select c from Cancellation c
                        where c.paymentAttemptId in (select pa.id from PaymentAttempt pa where pa.bookingId = :bookingId)
                        order by c.id desc
                        """, Cancellation.class)
                .setParameter("bookingId", bookingId)
                .setMaxResults(1)
                .getSingleResult();
    }

    private Long cancellationCount(Long bookingId) {
        return entityManager.createQuery("""
                        select count(c) from Cancellation c
                        where c.paymentAttemptId in (select pa.id from PaymentAttempt pa where pa.bookingId = :bookingId)
                        """, Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();
    }

    private List<SeatStatus> seatStatuses(Long bookingId) {
        return entityManager.createQuery("""
                        select s.status from SessionSeat s
                        where s.id in (select bs.sessionSeatId from BookingSeat bs where bs.bookingId = :bookingId)
                        """, SeatStatus.class)
                .setParameter("bookingId", bookingId)
                .getResultList();
    }

    private List<TicketStatus> ticketStatuses(Long bookingId) {
        return entityManager.createQuery("""
                        select t.status from Ticket t
                        where t.bookingSeatId in (select bs.id from BookingSeat bs where bs.bookingId = :bookingId)
                        """, TicketStatus.class)
                .setParameter("bookingId", bookingId)
                .getResultList();
    }

    // Toss 취소 성공 응답 (cancels[]에 주어진 금액의 거래 하나)
    private TossPaymentResponseDTO tossCanceledResponse(Long cancelAmount) {
        OffsetDateTime canceledAt = OffsetDateTime.now(ZoneOffset.ofHours(9));
        return new TossPaymentResponseDTO(
                "test-payment-key",
                "TIKIT_test0order",
                "CANCELED",
                "카드",
                AMOUNT,
                0L,
                TRANSACTION_KEY,
                canceledAt.minusDays(1),
                canceledAt.minusDays(1),
                List.of(new TossPaymentResponseDTO.Cancel(TRANSACTION_KEY, cancelAmount, REASON, canceledAt))
        );
    }

    private String accessTokenFor(Long memberId) {
        Member member = entityManager.find(Member.class, memberId);
        return jwtProvider.generateAccessToken(member);
    }
}