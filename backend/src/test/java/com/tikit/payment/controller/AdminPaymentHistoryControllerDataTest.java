package com.tikit.payment.controller;

import com.jayway.jsonpath.JsonPath;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
import com.tikit.payment.client.TossPaymentClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// 관리자 결제 처리 내역 API 데이터 통합 테스트 (테스트마다 롤백)
// 공용 DB라 다른 데이터가 섞일 수 있음 -> 필터·페이지는 이 테스트의 bookingId로 거르고, 최신순은 첫 페이지 맨 앞으로 확인
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class AdminPaymentHistoryControllerDataTest {

    private static final Long AMOUNT = 55000L;
    private static final String URL = "/api/v1/admin/payment-attempts";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    // 다른 결제 데이터 테스트와 같은 스프링 컨텍스트를 쓰려고 함께 둠 (이 테스트는 Toss를 부르지 않음)
    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @PersistenceContext
    private EntityManager entityManager;

    private PaymentTestDataHelper dataHelper;
    private Long adminId;

    @BeforeEach
    void setup() {
        dataHelper = new PaymentTestDataHelper(entityManager);
        adminId = dataHelper.createMember(MemberStatus.ACTIVE, Role.ADMIN);
    }

    @Test
    @DisplayName("결제 내역: 여러 회원의 결제가 최신순으로 나온다")
    void list_otherMembers_newestFirst() throws Exception {
        // given
        Long firstMemberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long secondMemberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long olderId = dataHelper.createPaymentAttempt(pendingBooking(firstMemberId), PaymentAttemptStatus.READY, AMOUNT);
        Long newerId = dataHelper.createPaymentAttempt(pendingBooking(secondMemberId), PaymentAttemptStatus.READY, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(get(URL).header("Authorization", "Bearer " + accessTokenFor(adminId)));
        String body = resultActions.andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + body);

        // then
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(longAt(body, "$.data.items[0].paymentAttemptId")).isEqualTo(newerId);
        assertThat(longAt(body, "$.data.items[1].paymentAttemptId")).isEqualTo(olderId);
        assertThat(longAt(body, "$.data.page")).isZero();
        assertThat(longAt(body, "$.data.size")).isEqualTo(20L);
    }

    @Test
    @DisplayName("결제 내역 항목은 회원 결제 결과 조회 API와 같은 값이다")
    void list_sameAsMemberView() throws Exception {
        // given (FAILED + 결제 대기 + 기한 안 -> retryAllowed true인 경우로 계산까지 비교)
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = pendingBooking(memberId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.FAILED, AMOUNT);

        // 메모리의 엔티티를 비워 관리자 목록도 DB 값(마이크로초)으로 읽게 함 (실제 요청과 같은 조건)
        entityManager.flush();
        entityManager.clear();

        // when
        String adminBody = mvc.perform(get(URL).param("bookingId", bookingId.toString())
                        .header("Authorization", "Bearer " + accessTokenFor(adminId)))
                .andReturn().getResponse().getContentAsString();
        String memberBody = mvc.perform(get("/api/v1/payment-attempts/" + paymentAttemptId)
                        .header("Authorization", "Bearer " + accessTokenFor(memberId)))
                .andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + adminBody);
        System.out.println("테스트: " + memberBody);

        // then
        for (String field : List.of("paymentAttemptId", "bookingId", "orderId", "orderName", "customerKey",
                "amount", "currency", "status", "bookingStatus", "expiresAt", "retryAllowed", "failureCode")) {
            Object adminValue = JsonPath.read(adminBody, "$.data.items[0]." + field);
            Object memberValue = JsonPath.read(memberBody, "$.data." + field);
            assertThat(String.valueOf(adminValue)).as(field).isEqualTo(String.valueOf(memberValue));
        }
        assertThat((Boolean) JsonPath.read(adminBody, "$.data.items[0].retryAllowed")).isTrue();
    }

    @Test
    @DisplayName("결제 내역: 예매 ID와 상태로 거르면 그 예매의 해당 상태만 나온다")
    void list_filterByBookingIdAndStatus() throws Exception {
        // given (같은 예매에 실패한 시도 1건 + 다시 준비한 시도 1건)
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = pendingBooking(memberId);
        Long failedId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.FAILED, AMOUNT);
        dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.READY, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(get(URL)
                .param("bookingId", bookingId.toString())
                .param("status", "FAILED")
                .header("Authorization", "Bearer " + accessTokenFor(adminId)));
        String body = resultActions.andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + body);

        // then
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(longAt(body, "$.data.totalElements")).isEqualTo(1L);
        assertThat(longAt(body, "$.data.items[0].paymentAttemptId")).isEqualTo(failedId);
        assertThat((String) JsonPath.read(body, "$.data.items[0].status")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("결제 내역: 3건을 2개씩 나누면 0페이지 2건, 1페이지 1건, 전체 3건·2페이지")
    void list_paging() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = pendingBooking(memberId);
        Long first = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.FAILED, AMOUNT);
        Long second = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.FAILED, AMOUNT);
        Long third = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.READY, AMOUNT);

        // when
        String page0 = mvc.perform(get(URL).param("bookingId", bookingId.toString())
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + accessTokenFor(adminId)))
                .andReturn().getResponse().getContentAsString();
        String page1 = mvc.perform(get(URL).param("bookingId", bookingId.toString())
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + accessTokenFor(adminId)))
                .andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + page0);
        System.out.println("테스트: " + page1);

        // then
        assertThat(longAt(page0, "$.data.totalElements")).isEqualTo(3L);
        assertThat(longAt(page0, "$.data.totalPages")).isEqualTo(2L);
        assertThat((List<?>) JsonPath.read(page0, "$.data.items")).hasSize(2);
        assertThat(longAt(page0, "$.data.items[0].paymentAttemptId")).isEqualTo(third);
        assertThat(longAt(page0, "$.data.items[1].paymentAttemptId")).isEqualTo(second);

        assertThat((List<?>) JsonPath.read(page1, "$.data.items")).hasSize(1);
        assertThat(longAt(page1, "$.data.items[0].paymentAttemptId")).isEqualTo(first);
        assertThat(longAt(page1, "$.data.page")).isEqualTo(1L);
    }

    @Test
    @DisplayName("결제 내역: size 101, page -1, 없는 status는 400 VALIDATION_ERROR")
    void list_invalidParams_badRequest() throws Exception {
        // given
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        // when
        int tooLargeSize = mvc.perform(get(URL).param("size", "101")
                .header("Authorization", "Bearer " + accessTokenFor(adminId))).andReturn().getResponse().getStatus();
        int negativePage = mvc.perform(get(URL).param("page", "-1")
                .header("Authorization", "Bearer " + accessTokenFor(adminId))).andReturn().getResponse().getStatus();
        ResultActions unknownStatus = mvc.perform(get(URL).param("status", "DONE")
                .header("Authorization", "Bearer " + accessTokenFor(adminId)));
        System.out.println("테스트: " + tooLargeSize + ", " + negativePage + ", "
                + unknownStatus.andReturn().getResponse().getContentAsString());

        // then
        assertThat(tooLargeSize).isEqualTo(errorCode.getStatus().value());
        assertThat(negativePage).isEqualTo(errorCode.getStatus().value());
        assertThat(unknownStatus.andReturn().getResponse().getStatus()).isEqualTo(errorCode.getStatus().value());
        assertThat((String) JsonPath.read(unknownStatus.andReturn().getResponse().getContentAsString(), "$.code"))
                .isEqualTo(errorCode.name());
    }

    @Test
    @DisplayName("결제 내역: MEMBER 토큰은 403")
    void list_memberToken_forbidden() throws Exception {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);

        // when
        int status = mvc.perform(get(URL).header("Authorization", "Bearer " + accessTokenFor(memberId)))
                .andReturn().getResponse().getStatus();
        System.out.println("테스트: " + status);

        // then
        assertThat(status).isEqualTo(403);
    }

    // 결제 대기 예매 (기한 5분 남음)
    private Long pendingBooking(Long memberId) {
        return dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
    }

    // JSON 숫자는 작으면 Integer로 읽히므로 long으로 바꿔 비교
    private long longAt(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private String accessTokenFor(Long memberId) {
        Member member = entityManager.find(Member.class, memberId);
        return jwtProvider.generateAccessToken(member);
    }
}