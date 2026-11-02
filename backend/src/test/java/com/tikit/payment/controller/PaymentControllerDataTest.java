package com.tikit.payment.controller;

import com.jayway.jsonpath.JsonPath;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 예매 데이터가 필요한 결제 통합 테스트 (테스트마다 롤백되어 DB에 데이터가 남지 않음)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class PaymentControllerDataTest {

    private static final Long AMOUNT = 55000L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    @PersistenceContext
    private EntityManager entityManager;

    private PaymentTestDataHelper dataHelper;

    @BeforeEach
    void setup() {
        dataHelper = new PaymentTestDataHelper(entityManager);
    }

    @Test
    @DisplayName("본인 결제 시도를 조회하면 200과 결제 정보를 반환")
    void get_payment_attempt_owned() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.READY, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/payment-attempts/" + paymentAttemptId)
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.paymentAttemptId").value(paymentAttemptId));
        resultActions.andExpect(jsonPath("$.data.status").value("READY"));
        resultActions.andExpect(jsonPath("$.data.amount").value(AMOUNT));
        resultActions.andExpect(jsonPath("$.data.orderName").value(PaymentTestDataHelper.PERFORMANCE_TITLE));
    }

    @Test
    @DisplayName("승인 처리 중인 결제 시도를 조회하면 PROCESSING을 그대로 반환")
    void get_payment_attempt_processing() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.PROCESSING, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/payment-attempts/" + paymentAttemptId)
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.status").value("PROCESSING"));
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(false));
    }

    @Test
    @DisplayName("결과 확인 중인 결제 시도를 조회하면 RECONCILING을 그대로 반환")
    void get_payment_attempt_reconciling() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.RECONCILING, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/payment-attempts/" + paymentAttemptId)
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.status").value("RECONCILING"));
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(false));
    }

    @Test
    @DisplayName("실패한 결제 시도를 결제 기한 안에 조회하면 retryAllowed가 true")
    void get_payment_attempt_failed_within_deadline() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.FAILED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/payment-attempts/" + paymentAttemptId)
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.status").value("FAILED"));
        resultActions.andExpect(jsonPath("$.data.failureCode").value(PaymentTestDataHelper.TEST_FAILURE_CODE));
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(true));
    }

    @Test
    @DisplayName("실패한 결제 시도를 결제 기한이 지난 뒤 조회하면 retryAllowed가 false")
    void get_payment_attempt_failed_after_deadline() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().minus(1, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.FAILED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/payment-attempts/" + paymentAttemptId)
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.status").value("FAILED"));
        resultActions.andExpect(jsonPath("$.data.bookingStatus").value("PENDING_PAYMENT"));
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(false));
    }

    @Test
    @DisplayName("다른 회원의 결제 시도를 조회하면 404를 반환")
    void get_payment_attempt_other_member() throws Exception {
        // given
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long otherMemberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(ownerId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.READY, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/payment-attempts/" + paymentAttemptId)
                        .header("Authorization", "Bearer " + accessTokenFor(otherMemberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("결제 시도가 없는 결제 대기 예매로 결제를 준비하면 201과 새 READY 결제 시도를 반환")
    void prepare_payment_attempt_created() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(201);
        resultActions.andExpect(jsonPath("$.data.bookingId").value(bookingId));
        resultActions.andExpect(jsonPath("$.data.status").value("READY"));
        resultActions.andExpect(jsonPath("$.data.amount").value(AMOUNT));
        resultActions.andExpect(jsonPath("$.data.orderId").value(startsWith("TIKIT_")));
    }

    @Test
    @DisplayName("READY 결제 시도가 있는 예매로 결제를 다시 준비하면 200과 같은 결제 시도를 반환")
    void prepare_payment_attempt_reuse_ready() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long existingAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.READY, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.paymentAttemptId").value(existingAttemptId));
        resultActions.andExpect(jsonPath("$.data.status").value("READY"));

        // 같은 예매에 결제 시도가 새로 생기지 않았는지 확인
        Long attemptCount = entityManager.createQuery(
                        "SELECT COUNT(pa) FROM PaymentAttempt pa WHERE pa.bookingId = :bookingId", Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();
        assertThat(attemptCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("정지된 회원이 결제를 준비하면 403 ACCOUNT_RESTRICTED를 반환")
    void prepare_payment_attempt_suspended_member() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.SUSPENDED, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.ACCOUNT_RESTRICTED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("역할이 MEMBER가 아닌 회원이 결제를 준비하면 403 ACCESS_DENIED를 반환")
    void prepare_payment_attempt_not_member_role() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MANAGER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("이미 확정된 예매로 결제를 준비하면 409 INVALID_BOOKING_STATE를 반환")
    void prepare_payment_attempt_confirmed_booking() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.INVALID_BOOKING_STATE;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("승인 처리 중인 결제 시도가 있는 예매로 결제를 준비하면 409 PAYMENT_IN_PROGRESS를 반환")
    void prepare_payment_attempt_payment_in_progress() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.PROCESSING, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.PAYMENT_IN_PROGRESS;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));

        // 새 결제 시도가 만들어지지 않았는지 확인
        Long attemptCount = entityManager.createQuery(
                        "SELECT COUNT(pa) FROM PaymentAttempt pa WHERE pa.bookingId = :bookingId", Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();
        assertThat(attemptCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("결제 기한이 지난 결제 대기 예매로 결제를 준비하면 409 BOOKING_EXPIRED를 반환")
    void prepare_payment_attempt_expired_booking() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().minus(1, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.BOOKING_EXPIRED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));

        // 결제 시도가 만들어지지 않았는지 확인
        Long attemptCount = entityManager.createQuery(
                        "SELECT COUNT(pa) FROM PaymentAttempt pa WHERE pa.bookingId = :bookingId", Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();
        assertThat(attemptCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("이전 결제가 실패했고 기한 안이면 새 결제 시도를 만들고 201을 반환")
    void prepare_payment_attempt_after_failed() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long failedAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.FAILED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(201);
        resultActions.andExpect(jsonPath("$.data.status").value("READY"));

        Long newAttemptId = ((Number) JsonPath.read(responseBody, "$.data.paymentAttemptId")).longValue();
        assertThat(newAttemptId).isNotEqualTo(failedAttemptId);

        // 실패한 결제 시도는 그대로 두고 새 결제 시도가 하나 더 생겼는지 확인
        Long attemptCount = entityManager.createQuery(
                        "SELECT COUNT(pa) FROM PaymentAttempt pa WHERE pa.bookingId = :bookingId", Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();
        assertThat(attemptCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("다른 회원의 예매로 결제를 준비하면 404를 반환")
    void prepare_payment_attempt_other_member_booking() throws Exception {
        // given
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long otherMemberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(ownerId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/payment-attempts")
                        .header("Authorization", "Bearer " + accessTokenFor(otherMemberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));

        // 남의 예매에 결제 시도가 만들어지지 않았는지 확인
        Long attemptCount = entityManager.createQuery(
                        "SELECT COUNT(pa) FROM PaymentAttempt pa WHERE pa.bookingId = :bookingId", Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();
        assertThat(attemptCount).isEqualTo(0L);
    }

    // DB에 넣은 회원 ID로 액세스 토큰 발급
    private String accessTokenFor(Long memberId) {
        Member member = Member.create("token-" + memberId + "@example.com", "encodedPassword", "tester", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", memberId);
        return jwtProvider.generateAccessToken(member);
    }
}