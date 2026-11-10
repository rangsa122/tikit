package com.tikit.payment.controller;

import com.tikit.common.idempotency.IdempotencyService;
import com.tikit.common.idempotency.IdempotencyStart;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.common.ErrorCode;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@AutoConfigureMockMvc // Mock(가짜) 환경에 MockMvc가 등록
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class PaymentControllerTest {

    private static final String VALID_CONFIRM_BODY = """
            {
              "paymentKey": "test-payment-key",
              "orderId": "TIKIT_test0order0id",
              "amount": 55000
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    // 컨트롤러 테스트는 DB 회원이 없는 가짜 토큰을 써서, 멱등 기록 저장(회원 FK)은 가짜로 대체
    @MockitoBean
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setup() {
        given(idempotencyService.start(any(), any(), any(), any())).willReturn(IdempotencyStart.started(1L));
    }

    @Test
    @DisplayName("토큰 없이 결제를 준비하면 401을 반환")
    void prepare_payment_attempt_without_token() throws Exception {
        // given

        // when
        ResultActions resultActions = mvc.perform(post("/api/v1/bookings/1/payment-attempts"));
        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("존재하지 않는 예매로 결제를 준비하면 404를 반환")
    void prepare_payment_attempt_booking_not_found() throws Exception {
        // given
        Member member = Member.create(
                "payment-test@example.com",
                "encodedPassword",
                "tester",
                Role.MEMBER
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtProvider.generateAccessToken(member);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/bookings/999999999/payment-attempts")
                        .header("Authorization", "Bearer " + accessToken)
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
    @DisplayName("토큰 없이 결제 결과를 조회하면 401을 반환")
    void get_payment_attempt_without_token() throws Exception {
        // given

        // when
        ResultActions resultActions = mvc.perform(get("/api/v1/payment-attempts/1"));
        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("존재하지 않는 결제 시도를 조회하면 404를 반환")
    void get_payment_attempt_not_found() throws Exception {
        // given
        Member member = Member.create(
                "payment-test@example.com",
                "encodedPassword",
                "tester",
                Role.MEMBER
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtProvider.generateAccessToken(member);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/payment-attempts/999999999")
                        .header("Authorization", "Bearer " + accessToken)
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
    @DisplayName("토큰 없이 결제를 승인하면 401을 반환")
    void confirm_payment_without_token() throws Exception {
        // given

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/payment-attempts/1/confirmation")
                        .header("Idempotency-Key", "confirm-controller-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONFIRM_BODY)
        );
        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("존재하지 않는 결제 시도를 승인하면 404를 반환")
    void confirm_payment_attempt_not_found() throws Exception {
        // given
        Member member = Member.create(
                "payment-test@example.com",
                "encodedPassword",
                "tester",
                Role.MEMBER
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtProvider.generateAccessToken(member);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/payment-attempts/999999999/confirmation")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "confirm-controller-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONFIRM_BODY)
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
    @DisplayName("주문 번호 형식이 잘못된 요청으로 결제를 승인하면 400을 반환")
    void confirm_payment_invalid_order_id() throws Exception {
        // given
        Member member = Member.create(
                "payment-test@example.com",
                "encodedPassword",
                "tester",
                Role.MEMBER
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtProvider.generateAccessToken(member);

        String invalidBody = """
                {
                  "paymentKey": "test-payment-key",
                  "orderId": "bad order!",
                  "amount": 55000
                }
                """;

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/payment-attempts/999999999/confirmation")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "confirm-controller-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        resultActions.andExpect(jsonPath("$.message").value("주문 번호 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더 없이 결제를 승인하면 400 VALIDATION_ERROR를 반환")
    void confirm_payment_without_idempotency_key() throws Exception {
        // given
        Member member = Member.create(
                "payment-test@example.com",
                "encodedPassword",
                "tester",
                Role.MEMBER
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtProvider.generateAccessToken(member);

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/payment-attempts/1/confirmation")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONFIRM_BODY)
        );
        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
        resultActions.andExpect(jsonPath("$.message").value("Idempotency-Key 헤더가 필요합니다."));
    }

    @Test
    @DisplayName("토큰 없이 전체 취소 예상 금액을 조회하면 401을 반환")
    void get_cancellation_quote_without_token() throws Exception {
        // given

        // when
        ResultActions resultActions = mvc.perform(get("/api/v1/bookings/1/cancellation-quote"));
        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        assertThat(httpStatusCode).isEqualTo(errorCode.getStatus().value());
        resultActions.andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @Test
    @DisplayName("존재하지 않는 예매의 전체 취소 예상 금액을 조회하면 404를 반환")
    void get_cancellation_quote_booking_not_found() throws Exception {
        // given
        Member member = Member.create(
                "payment-test@example.com",
                "encodedPassword",
                "tester",
                Role.MEMBER
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtProvider.generateAccessToken(member);

        // when
        ResultActions resultActions = mvc.perform(
                get("/api/v1/bookings/999999999/cancellation-quote")
                        .header("Authorization", "Bearer " + accessToken)
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
}