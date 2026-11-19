package com.tikit.payment.controller;

import com.jayway.jsonpath.JsonPath;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobStatus;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.support.PaymentTestDataHelper;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 관리자 결제·환불 결과 재확인 요청 API 통합 테스트 (Toss는 부르지 않는지 확인, 테스트마다 롤백)
// 테스트 데이터는 커밋되지 않아 복구 스케줄러가 테스트 작업을 꺼내지 않음
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class AdminReconciliationControllerDataTest {

    private static final Long AMOUNT = 55000L;
    private static final String REASON = "자동 복구 포기 건 재확인";
    private static final String GIVE_UP_CODE = "RETRY_LIMIT_EXCEEDED";

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
    @DisplayName("결제 재확인: 자동 복구를 포기한 작업이 202 QUEUED, 시도 횟수 0, 바로 실행되게 바뀐다 (Toss 미호출)")
    void payment_failedJob_requeued() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long paymentAttemptId = reconcilingPaymentAttempt();
        Long jobId = createGivenUpJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId);

        // when
        ResultActions resultActions = mvc.perform(
                paymentRecheckRequest(paymentAttemptId, adminId, newIdempotencyKey(), REASON));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(202);
        resultActions.andExpect(jsonPath("$.code").value("SUCCESS"));
        resultActions.andExpect(jsonPath("$.data.status").value("QUEUED"));
        assertThat(((Number) JsonPath.read(responseBody, "$.data.jobId")).longValue()).isEqualTo(jobId);
        assertThat(((Number) JsonPath.read(responseBody, "$.data.resourceId")).longValue()).isEqualTo(paymentAttemptId);

        BackgroundJob job = findJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getNextRunAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(job.getLastErrorCode()).isEqualTo(GIVE_UP_CODE);
        then(tossPaymentClient).should(never()).getPayment(any());
    }

    @Test
    @DisplayName("결제 재확인: 복구 작업이 없는 결과 확인 중 결제면 바로 실행되는 작업을 새로 만든다")
    void payment_noJob_created() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long paymentAttemptId = reconcilingPaymentAttempt();

        // when
        ResultActions resultActions = mvc.perform(
                paymentRecheckRequest(paymentAttemptId, adminId, newIdempotencyKey(), REASON));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + responseBody);

        // then
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(202);
        resultActions.andExpect(jsonPath("$.data.status").value("QUEUED"));

        assertThat(jobCount(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId)).isEqualTo(1L);
        BackgroundJob job = findJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId);
        assertThat(((Number) JsonPath.read(responseBody, "$.data.jobId")).longValue()).isEqualTo(job.getId());
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getNextRunAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("환불 재확인: 자동 복구를 포기한 환불 작업이 202 QUEUED로 바뀌고 Toss 환불은 다시 요청하지 않는다")
    void cancellation_failedJob_requeued() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long cancellationId = reconcilingCancellation();
        Long jobId = createGivenUpJob(BackgroundJobType.CANCELLATION_RECONCILE, cancellationId);

        // when
        ResultActions resultActions = mvc.perform(
                cancellationRecheckRequest(cancellationId, adminId, newIdempotencyKey(), REASON));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + responseBody);

        // then
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(202);
        resultActions.andExpect(jsonPath("$.data.status").value("QUEUED"));
        assertThat(((Number) JsonPath.read(responseBody, "$.data.jobId")).longValue()).isEqualTo(jobId);
        assertThat(((Number) JsonPath.read(responseBody, "$.data.resourceId")).longValue()).isEqualTo(cancellationId);

        BackgroundJob job = findJob(BackgroundJobType.CANCELLATION_RECONCILE, cancellationId);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isZero();
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("결제 재확인: 이미 승인된 결제면 409 STATE_CONFLICT, 작업을 만들지 않는다")
    void payment_approved_conflict() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(ownerId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                paymentRecheckRequest(paymentAttemptId, adminId, newIdempotencyKey(), REASON));

        System.out.println("테스트: " + resultActions.andReturn().getResponse().getContentAsString());

        // then
        ErrorCode errorCode = ErrorCode.STATE_CONFLICT;
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        assertThat(jobCount(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId)).isZero();
    }

    @Test
    @DisplayName("같은 멱등키·같은 사유로 다시 요청하면 200과 같은 작업, 작업은 하나만 있다")
    void sameKey_replay() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long paymentAttemptId = reconcilingPaymentAttempt();
        createGivenUpJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId);
        String idempotencyKey = newIdempotencyKey();

        // when
        ResultActions first = mvc.perform(paymentRecheckRequest(paymentAttemptId, adminId, idempotencyKey, REASON));
        ResultActions second = mvc.perform(paymentRecheckRequest(paymentAttemptId, adminId, idempotencyKey, REASON));

        String firstBody = first.andReturn().getResponse().getContentAsString();
        String secondBody = second.andReturn().getResponse().getContentAsString();

        System.out.println("테스트: " + firstBody);
        System.out.println("테스트: " + secondBody);

        // then
        assertThat(first.andReturn().getResponse().getStatus()).isEqualTo(202);
        assertThat(second.andReturn().getResponse().getStatus()).isEqualTo(200);
        second.andExpect(jsonPath("$.data.status").value("QUEUED"));
        assertThat(((Number) JsonPath.read(secondBody, "$.data.jobId")).longValue())
                .isEqualTo(((Number) JsonPath.read(firstBody, "$.data.jobId")).longValue());
        assertThat(jobCount(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 멱등키로 다른 사유를 보내면 409 IDEMPOTENCY_CONFLICT")
    void sameKey_differentReason_conflict() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long paymentAttemptId = reconcilingPaymentAttempt();
        String idempotencyKey = newIdempotencyKey();

        // when
        ResultActions first = mvc.perform(paymentRecheckRequest(paymentAttemptId, adminId, idempotencyKey, REASON));
        ResultActions second = mvc.perform(
                paymentRecheckRequest(paymentAttemptId, adminId, idempotencyKey, "다른 사유"));

        System.out.println("테스트: " + second.andReturn().getResponse().getContentAsString());

        // then
        assertThat(first.andReturn().getResponse().getStatus()).isEqualTo(202);
        ErrorCode errorCode = ErrorCode.IDEMPOTENCY_CONFLICT;
        assertThat(second.andReturn().getResponse().getStatus()).isEqualTo(errorCode.getStatus().value());
        second.andExpect(jsonPath("$.code").value(errorCode.name()));
        assertThat(jobCount(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("MEMBER 토큰으로 재확인을 요청하면 403, 작업이 바뀌지 않는다")
    void memberToken_forbidden() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long paymentAttemptId = reconcilingPaymentAttempt();
        createGivenUpJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId);

        // when
        ResultActions resultActions = mvc.perform(
                paymentRecheckRequest(paymentAttemptId, memberId, newIdempotencyKey(), REASON));

        System.out.println("테스트: " + resultActions.andReturn().getResponse().getContentAsString());

        // then
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(403);
        BackgroundJob job = findJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 VALIDATION_ERROR, 작업을 만들지 않는다")
    void missingIdempotencyKey_badRequest() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long paymentAttemptId = reconcilingPaymentAttempt();

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/admin/payment-attempts/" + paymentAttemptId + "/reconciliation-requests")
                        .header("Authorization", "Bearer " + accessTokenFor(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(REASON)));

        System.out.println("테스트: " + resultActions.andReturn().getResponse().getContentAsString());

        // then
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        assertThat(jobCount(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId)).isZero();
    }

    @Test
    @DisplayName("사유가 비어 있으면 400 VALIDATION_ERROR, 작업을 만들지 않는다")
    void blankReason_badRequest() throws Exception {
        // given
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long paymentAttemptId = reconcilingPaymentAttempt();

        // when
        ResultActions resultActions = mvc.perform(
                paymentRecheckRequest(paymentAttemptId, adminId, newIdempotencyKey(), " "));

        System.out.println("테스트: " + resultActions.andReturn().getResponse().getContentAsString());

        // then
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        assertThat(jobCount(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId)).isZero();
    }

    // 결과 확인 중인 결제 (결제 기한 안의 결제 대기 예매)
    private Long reconcilingPaymentAttempt() {
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(ownerId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        return dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.RECONCILING, AMOUNT);
    }

    // 결과 확인 중인 취소 (확정 예매 + 승인된 결제)
    private Long reconcilingCancellation() {
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(ownerId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        return dataHelper.createCancellation(paymentAttemptId, ownerId, CancellationInitiatorRole.MEMBER,
                CancellationStatus.RECONCILING, AMOUNT);
    }

    // 자동 복구를 포기한 작업 (실제 흐름처럼 점유 -> 포기, 시도 횟수 1) -> 작업 ID
    private Long createGivenUpJob(BackgroundJobType type, Long resourceId) {
        Instant now = Instant.now();
        BackgroundJob job = BackgroundJob.queue(type, resourceId, now.minus(1, ChronoUnit.HOURS));
        job.lease("test-owner", now, Duration.ofMinutes(5));
        job.giveUp("test-owner", GIVE_UP_CODE);
        entityManager.persist(job);
        entityManager.flush();
        return job.getId();
    }

    // 요청이 바꾼 값을 DB에서 다시 읽음 (같은 트랜잭션의 엔티티 캐시 대신)
    private BackgroundJob findJob(BackgroundJobType type, Long resourceId) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.createQuery("""
                        select j from BackgroundJob j
                        where j.deduplicationKey = :deduplicationKey
                        """, BackgroundJob.class)
                .setParameter("deduplicationKey", type.deduplicationKey(resourceId))
                .getSingleResult();
    }

    private Long jobCount(BackgroundJobType type, Long resourceId) {
        return entityManager.createQuery("""
                        select count(j) from BackgroundJob j
                        where j.deduplicationKey = :deduplicationKey
                        """, Long.class)
                .setParameter("deduplicationKey", type.deduplicationKey(resourceId))
                .getSingleResult();
    }

    private RequestBuilder paymentRecheckRequest(Long paymentAttemptId, Long accountId,
                                                 String idempotencyKey, String reason) {
        return post("/api/v1/admin/payment-attempts/" + paymentAttemptId + "/reconciliation-requests")
                .header("Authorization", "Bearer " + accessTokenFor(accountId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(reason));
    }

    private RequestBuilder cancellationRecheckRequest(Long cancellationId, Long accountId,
                                                      String idempotencyKey, String reason) {
        return post("/api/v1/admin/cancellations/" + cancellationId + "/reconciliation-requests")
                .header("Authorization", "Bearer " + accessTokenFor(accountId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(reason));
    }

    private String requestBody(String reason) {
        return """
                {
                  "reason": "%s"
                }
                """.formatted(reason);
    }

    // 테스트마다 겹치지 않는 멱등키
    private String newIdempotencyKey() {
        return "admin-reconcile-" + UUID.randomUUID();
    }

    private String accessTokenFor(Long memberId) {
        Member member = entityManager.find(Member.class, memberId);
        return jwtProvider.generateAccessToken(member);
    }
}