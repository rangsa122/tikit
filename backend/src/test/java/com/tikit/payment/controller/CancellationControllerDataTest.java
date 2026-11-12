package com.tikit.payment.controller;

import com.jayway.jsonpath.JsonPath;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.common.ErrorCode;
import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobStatus;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossCancelRequestDTO;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 예매 전체 취소 API 예매 데이터 통합 테스트 (Toss만 가짜, 테스트마다 롤백)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class CancellationControllerDataTest {

    private static final Long AMOUNT = 55000L;
    private static final String TRANSACTION_KEY = "test-cancel-transaction-key";

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
    @DisplayName("정상 취소: 202 SUCCEEDED, 거래 키 저장, 예매 CANCELED·좌석 AVAILABLE·티켓 VOID")
    void cancel_booking_succeeded() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(202);
        resultActions.andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        resultActions.andExpect(jsonPath("$.data.refundAmount").value(AMOUNT));
        resultActions.andExpect(jsonPath("$.data.feeAmount").value(0));
        resultActions.andExpect(jsonPath("$.data.initiatorRole").value("MEMBER"));

        then(tossPaymentClient).should().cancelPayment(any(), eq(new TossCancelRequestDTO("개인 사정", AMOUNT)), any());

        Cancellation cancellation = latestCancellation(bookingId);
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
        assertThat(cancellation.getPgTransactionKey()).isEqualTo(TRANSACTION_KEY);

        assertThat(entityManager.find(Booking.class, bookingId).getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(seatStatuses(bookingId)).containsExactly(SeatStatus.AVAILABLE);
        assertThat(ticketStatuses(bookingId)).containsExactly(TicketStatus.VOID);
    }

    @Test
    @DisplayName("견적과 금액이 다르면 409 CANCELLATION_QUOTE_CHANGED, Toss를 부르지 않는다")
    void cancel_booking_quote_changed() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, 1000L, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.CANCELLATION_QUOTE_CHANGED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
        assertThat(entityManager.find(Booking.class, bookingId).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("결제 대기 예매는 422 CANCELLATION_NOT_ALLOWED")
    void cancel_booking_not_confirmed() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.CANCELLATION_NOT_ALLOWED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("Toss가 거절하면 202 FAILED + 재시도 가능, 예매·좌석·티켓은 그대로")
    void cancel_booking_toss_declined() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        given(tossPaymentClient.cancelPayment(any(), any(), any()))
                .willThrow(new TossApiException(400, "NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제입니다."));

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(202);
        resultActions.andExpect(jsonPath("$.data.status").value("FAILED"));
        resultActions.andExpect(jsonPath("$.data.failureCode").value("NOT_CANCELABLE_PAYMENT"));
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(true));

        assertThat(entityManager.find(Booking.class, bookingId).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seatStatuses(bookingId)).containsExactly(SeatStatus.SOLD);
        assertThat(ticketStatuses(bookingId)).containsExactly(TicketStatus.VALID);
    }

    @Test
    @DisplayName("Toss 응답을 받지 못하면 202 RECONCILING, 예매는 그대로")
    void cancel_booking_result_unknown() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        given(tossPaymentClient.cancelPayment(any(), any(), any()))
                .willThrow(new TossResultUnknownException("Read timed out"));

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(202);
        resultActions.andExpect(jsonPath("$.data.status").value("RECONCILING"));
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(false));
        assertThat(entityManager.find(Booking.class, bookingId).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("결과 확인 중인 취소가 있는데 다른 키로 다시 요청하면 409 CANCELLATION_IN_PROGRESS")
    void cancel_booking_in_progress() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        given(tossPaymentClient.cancelPayment(any(), any(), any()))
                .willThrow(new TossResultUnknownException("Read timed out"));
        mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", newIdempotencyKey()));

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.CANCELLATION_IN_PROGRESS;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("다른 회원의 예매를 취소하면 404")
    void cancel_booking_other_member() throws Exception {
        // given
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long otherMemberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, otherMemberId, AMOUNT, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("같은 멱등키로 다시 취소 요청하면 Toss를 다시 부르지 않고 200과 같은 취소 건의 현재 상태를 반환")
    void cancel_booking_same_idempotency_key_replays() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        String idempotencyKey = newIdempotencyKey();
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));

        String firstBody = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", idempotencyKey))
                .andReturn().getResponse().getContentAsString();
        Integer firstCancellationId = JsonPath.read(firstBody, "$.data.cancellationId");

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", idempotencyKey));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.cancellationId").value(firstCancellationId));
        resultActions.andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        then(tossPaymentClient).should(times(1)).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("같은 멱등키로 다른 내용(사유)을 보내면 409 IDEMPOTENCY_CONFLICT")
    void cancel_booking_same_idempotency_key_different_body_conflict() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        String idempotencyKey = newIdempotencyKey();
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));
        mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", idempotencyKey));

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/cancellations")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("다른 사유", AMOUNT, "v1"))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.IDEMPOTENCY_CONFLICT;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더 없이 취소하면 400 VALIDATION_ERROR")
    void cancel_booking_without_idempotency_key() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/cancellations")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("개인 사정", AMOUNT, "v1"))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.message").value("Idempotency-Key 헤더가 필요합니다."));
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("취소를 시작하면 환불 결과 확인 작업 1건이 QUEUED로 저장되고, 2분 뒤에 실행되도록 예약")
    void cancel_booking_schedules_reconcile_job() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));
        Instant before = Instant.now();

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT, "v1", newIdempotencyKey()));
        Instant after = Instant.now();

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(202);

        Cancellation cancellation = latestCancellation(bookingId);
        List<BackgroundJob> jobs = reconcileJobsOf(cancellation.getId());
        assertThat(jobs).hasSize(1);

        BackgroundJob job = jobs.getFirst();
        assertThat(job.getType()).isEqualTo(BackgroundJobType.CANCELLATION_RECONCILE);
        assertThat(job.getResourceType()).isEqualTo("CANCELLATION");
        assertThat(job.getResourceId()).isEqualTo(cancellation.getId());
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getNextRunAt()).isBetween(before.plus(Duration.ofMinutes(2)), after.plus(Duration.ofMinutes(2)));
    }

    @Test
    @DisplayName("견적이 바뀌어 취소를 거절하면 환불 결과 확인 작업도 저장되지 않음")
    void cancel_booking_quote_changed_no_job() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(memberId);
        long jobCountBefore = cancellationJobCount();

        // when
        ResultActions resultActions = mvc.perform(cancelRequest(bookingId, memberId, AMOUNT - 1, "v1", newIdempotencyKey()));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.CANCELLATION_QUOTE_CHANGED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        assertThat(cancellationJobCount()).isEqualTo(jobCountBefore);
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    // 기한 전 확정 예매 + 승인된 결제 (좌석 SOLD, 티켓 1장)
    private Long confirmedBooking(Long memberId) {
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        return bookingId;
    }

    private RequestBuilder cancelRequest(Long bookingId, Long memberId, Long expectedRefundAmount,
                                         String policyVersion, String idempotencyKey) {
        MockHttpServletRequestBuilder builder = post("/api/v1/bookings/" + bookingId + "/cancellations")
                .header("Authorization", "Bearer " + accessTokenFor(memberId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody("개인 사정", expectedRefundAmount, policyVersion));
        return builder;
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
        return "cancel-" + UUID.randomUUID();
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
                List.of(new TossPaymentResponseDTO.Cancel(TRANSACTION_KEY, cancelAmount, "개인 사정", canceledAt))
        );
    }

    private String accessTokenFor(Long memberId) {
        Member member = entityManager.find(Member.class, memberId);
        return jwtProvider.generateAccessToken(member);
    }


    // 이 취소의 결과 확인 작업 목록 (중복 방지 키로 조회)
    private List<BackgroundJob> reconcileJobsOf(Long cancellationId) {
        return entityManager.createQuery(
                        "select j from BackgroundJob j where j.deduplicationKey = :key", BackgroundJob.class)
                .setParameter("key", BackgroundJobType.CANCELLATION_RECONCILE.deduplicationKey(cancellationId))
                .getResultList();
    }

    // 환불 결과 확인 작업 전체 개수 (거절된 요청 전후 비교용)
    private long cancellationJobCount() {
        return entityManager.createQuery(
                        "select count(j) from BackgroundJob j where j.type = :type", Long.class)
                .setParameter("type", BackgroundJobType.CANCELLATION_RECONCILE)
                .getSingleResult();
    }
}