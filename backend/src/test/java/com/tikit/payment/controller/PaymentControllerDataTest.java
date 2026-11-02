package com.tikit.payment.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.BookingStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    // DB에 넣은 회원 ID로 액세스 토큰 발급
    private String accessTokenFor(Long memberId) {
        Member member = Member.create("token-" + memberId + "@example.com", "encodedPassword", "tester", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", memberId);
        return jwtProvider.generateAccessToken(member);
    }
}