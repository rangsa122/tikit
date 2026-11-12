package com.tikit.payment.controller;

import com.jayway.jsonpath.JsonPath;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 전체 취소 및 환불 결과 조회 API 예매 데이터 통합 테스트 (테스트마다 롤백)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class CancellationQueryControllerDataTest {

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
    @DisplayName("본인 예매의 성공한 취소를 조회하면 200과 명세 12필드를 한국 시간으로 반환")
    void get_cancellation_succeeded_owned() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long paymentAttemptId = approvedPayment(memberId, BookingStatus.CANCELED);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, memberId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.SUCCEEDED, AMOUNT);
        Long bookingId = entityManager.find(PaymentAttempt.class, paymentAttemptId).getBookingId();

        // when
        ResultActions resultActions = mvc.perform(getCancellationRequest(cancellationId, memberId));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.code").value("SUCCESS"));
        resultActions.andExpect(jsonPath("$.data.cancellationId").value(cancellationId));
        resultActions.andExpect(jsonPath("$.data.bookingId").value(bookingId));
        resultActions.andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        resultActions.andExpect(jsonPath("$.data.refundAmount").value(AMOUNT));
        resultActions.andExpect(jsonPath("$.data.feeAmount").value(0));
        resultActions.andExpect(jsonPath("$.data.reason").value(PaymentTestDataHelper.CANCELLATION_REASON));
        resultActions.andExpect(jsonPath("$.data.initiatorRole").value("MEMBER"));
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(false));
        resultActions.andExpect(jsonPath("$.data.failureCode").doesNotExist());
        resultActions.andExpect(jsonPath("$.data.createdAt").exists());
        resultActions.andExpect(jsonPath("$.data.serverTime").exists());

        String completedAt = JsonPath.read(responseBody, "$.data.completedAt");
        assertThat(OffsetDateTime.parse(completedAt).getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    @DisplayName("관리자가 수동 환불한 취소도 예매한 회원이 조회하면 200과 취소 주체 ADMIN을 반환")
    void get_cancellation_admin_refund_owner() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
        Long paymentAttemptId = approvedPayment(memberId, BookingStatus.CANCELED);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, adminId,
                CancellationInitiatorRole.ADMIN, CancellationStatus.SUCCEEDED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(getCancellationRequest(cancellationId, memberId));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.cancellationId").value(cancellationId));
        resultActions.andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        resultActions.andExpect(jsonPath("$.data.initiatorRole").value("ADMIN"));
    }

    @Test
    @DisplayName("결과 확인 중인 취소를 조회하면 200과 RECONCILING, 완료 시각 없음, 재시도 불가를 반환")
    void get_cancellation_reconciling() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long paymentAttemptId = approvedPayment(memberId, BookingStatus.CONFIRMED);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, memberId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.RECONCILING, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(getCancellationRequest(cancellationId, memberId));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.status").value("RECONCILING"));
        resultActions.andExpect(jsonPath("$.data.completedAt").doesNotExist());
        resultActions.andExpect(jsonPath("$.data.retryAllowed").value(false));
    }

    @Test
    @DisplayName("실패한 취소를 조회하면 200과 실패 코드, 확정 예매·기한 전이라 재시도 가능을 반환")
    void get_cancellation_failed_retry_allowed() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long paymentAttemptId = approvedPayment(memberId, BookingStatus.CONFIRMED);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, memberId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.FAILED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(getCancellationRequest(cancellationId, memberId));

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
    @DisplayName("다른 회원의 취소를 조회하면 404를 반환하고 취소 정보는 응답하지 않음")
    void get_cancellation_other_members_cancellation() throws Exception {
        // given
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long otherMemberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long paymentAttemptId = approvedPayment(ownerId, BookingStatus.CANCELED);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, ownerId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.SUCCEEDED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(getCancellationRequest(cancellationId, otherMemberId));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        resultActions.andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("없는 취소를 조회하면 다른 회원의 취소와 같은 404를 반환")
    void get_cancellation_not_found() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long missingCancellationId = Long.MAX_VALUE;

        // when
        ResultActions resultActions = mvc.perform(getCancellationRequest(missingCancellationId, memberId));

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
    @DisplayName("토큰 없이 취소 결과를 조회하면 401을 반환")
    void get_cancellation_without_token() throws Exception {
        // given
        Long cancellationId = 1L;

        // when
        ResultActions resultActions = mvc.perform(get("/api/v1/cancellations/" + cancellationId));

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    // 회원 본인의 승인된 결제 (예매 상태 지정) -> 결제 시도 ID
    private Long approvedPayment(Long memberId, BookingStatus bookingStatus) {
        Long bookingId = dataHelper.createBooking(memberId, bookingStatus,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        return dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
    }

    // 취소 결과 조회 요청 (회원 토큰 포함)
    private RequestBuilder getCancellationRequest(Long cancellationId, Long memberId) {
        return get("/api/v1/cancellations/" + cancellationId)
                .header("Authorization", "Bearer " + accessTokenFor(memberId));
    }

    private String accessTokenFor(Long memberId) {
        Member member = entityManager.find(Member.class, memberId);
        return jwtProvider.generateAccessToken(member);
    }
}