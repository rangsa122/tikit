package com.tikit.payment.controller;

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
}