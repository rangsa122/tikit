package com.tikit.payment.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 결제 승인 API 예매 데이터 통합 테스트 (Toss만 가짜, 테스트마다 롤백)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class PaymentConfirmControllerDataTest {

    private static final Long AMOUNT = 55000L;

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
    @DisplayName("Toss가 승인하면 200과 APPROVED를 반환하고 승인 시각을 저장")
    void confirm_payment_approved() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.READY, AMOUNT);
        String orderId = entityManager.find(PaymentAttempt.class, paymentAttemptId).getOrderId();

        OffsetDateTime approvedAt = OffsetDateTime.now(ZoneOffset.ofHours(9));
        given(tossPaymentClient.confirmPayment(any(), any()))
                .willReturn(tossApprovedResponse(orderId, AMOUNT, approvedAt));

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/payment-attempts/" + paymentAttemptId + "/confirmation")
                        .header("Authorization", "Bearer " + accessTokenFor(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequestBody(orderId, AMOUNT))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);
        resultActions.andExpect(jsonPath("$.data.status").value("APPROVED"));

        PaymentAttempt attempt = entityManager.find(PaymentAttempt.class, paymentAttemptId);
        assertThat(attempt.getApprovedAt()).isEqualTo(approvedAt.toInstant());
        then(tossPaymentClient).should().confirmPayment(any(), eq(attempt.getPgIdempotencyKey()));
    }

    // 승인 요청 본문 (paymentKey는 결제창 인증 후 Toss가 주는 값이라 테스트에서는 임의 값)
    private String confirmRequestBody(String orderId, Long amount) {
        return """
                {
                  "paymentKey": "test-confirm-key-%s",
                  "orderId": "%s",
                  "amount": %d
                }
                """.formatted(orderId, orderId, amount);
    }

    // Toss 승인 성공 응답 (status DONE + 승인 시각)
    private TossPaymentResponseDTO tossApprovedResponse(String orderId, Long amount, OffsetDateTime approvedAt) {
        return new TossPaymentResponseDTO(
                "test-confirm-key-" + orderId,
                orderId,
                "DONE",
                "카드",
                amount,
                amount,
                "test-transaction-key",
                approvedAt.minusSeconds(5),
                approvedAt,
                List.of()
        );
    }

    // DB에 넣은 회원 ID로 액세스 토큰 발급
    private String accessTokenFor(Long memberId) {
        Member member = Member.create("token-" + memberId + "@example.com", "encodedPassword", "tester", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", memberId);
        return jwtProvider.generateAccessToken(member);
    }
}