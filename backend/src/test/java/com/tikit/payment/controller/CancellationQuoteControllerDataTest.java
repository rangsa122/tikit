package com.tikit.payment.controller;

import com.jayway.jsonpath.JsonPath;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 전체 취소 예상 금액 API 예매 데이터 통합 테스트 (테스트마다 롤백)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class CancellationQuoteControllerDataTest {

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
    @DisplayName("기한 전의 확정 예매는 200과 취소 가능, 결제액 전액 환불, 예매 스냅샷의 정책, 기한을 반환")
    void get_cancellation_quote_confirmed_eligible() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/bookings/" + bookingId + "/cancellation-quote")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.bookingId").value(bookingId));
        resultActions.andExpect(jsonPath("$.data.eligible").value(true));
        resultActions.andExpect(jsonPath("$.data.reasonCode").doesNotExist());
        resultActions.andExpect(jsonPath("$.data.paidAmount").value(AMOUNT));
        resultActions.andExpect(jsonPath("$.data.feeAmount").value(0));
        resultActions.andExpect(jsonPath("$.data.refundAmount").value(AMOUNT));
        resultActions.andExpect(jsonPath("$.data.policyVersion").value("v1"));

        Booking booking = entityManager.find(Booking.class, bookingId);
        String deadline = JsonPath.read(responseBody, "$.data.cancellationDeadline");
        assertThat(OffsetDateTime.parse(deadline).toInstant())
                .isEqualTo(booking.getCancellationDeadlineSnapshot());
    }

    @Test
    @DisplayName("결제 대기 예매는 200과 취소 불가(BOOKING_NOT_CONFIRMED), 결제액 0원을 반환")
    void get_cancellation_quote_pending_payment_not_confirmed() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/bookings/" + bookingId + "/cancellation-quote")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.eligible").value(false));
        resultActions.andExpect(jsonPath("$.data.reasonCode").value("BOOKING_NOT_CONFIRMED"));
        resultActions.andExpect(jsonPath("$.data.paidAmount").value(0));
        resultActions.andExpect(jsonPath("$.data.refundAmount").value(0));
    }

    @Test
    @DisplayName("다른 회원의 예매를 조회하면 404를 반환")
    void get_cancellation_quote_other_members_booking() throws Exception {
        // given
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long otherMemberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(ownerId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/bookings/" + bookingId + "/cancellation-quote")
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

    private String accessTokenFor(Long memberId) {
        Member member = entityManager.find(Member.class, memberId);
        return jwtProvider.generateAccessToken(member);
    }
}