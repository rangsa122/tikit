package com.tikit.payment.controller;

import com.jayway.jsonpath.JsonPath;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
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

// 관리자 전체 취소 및 환불 내역 API 데이터 통합 테스트 (테스트마다 롤백)
// 공용 DB라 다른 데이터가 섞일 수 있음 -> 필터·페이지는 이 테스트의 bookingId로 거르고, 최신순은 첫 페이지 맨 앞으로 확인
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class AdminCancellationHistoryControllerDataTest {

    private static final Long AMOUNT = 55000L;
    private static final String URL = "/api/v1/admin/cancellations";

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
    @DisplayName("취소 내역: 회원 취소와 관리자 환불이 함께 최신순으로 나오고 initiatorRole로 구분된다")
    void list_memberAndAdmin_newestFirst() throws Exception {
        // given
        Long firstOwner = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long secondOwner = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long memberCancelId = dataHelper.createCancellation(approvedAttempt(firstOwner), firstOwner,
                CancellationInitiatorRole.MEMBER, CancellationStatus.SUCCEEDED, AMOUNT);
        Long adminCancelId = dataHelper.createCancellation(approvedAttempt(secondOwner), adminId,
                CancellationInitiatorRole.ADMIN, CancellationStatus.SUCCEEDED, AMOUNT);

        // when
        ResultActions resultActions = mvc.perform(get(URL).header("Authorization", "Bearer " + accessTokenFor(adminId)));
        String body = resultActions.andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + body);

        // then
        assertThat(resultActions.andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(longAt(body, "$.data.items[0].cancellationId")).isEqualTo(adminCancelId);
        assertThat((String) JsonPath.read(body, "$.data.items[0].initiatorRole")).isEqualTo("ADMIN");
        assertThat(longAt(body, "$.data.items[1].cancellationId")).isEqualTo(memberCancelId);
        assertThat((String) JsonPath.read(body, "$.data.items[1].initiatorRole")).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("취소 내역: 예매 ID와 상태로 거르면 그 예매의 해당 상태만 나온다")
    void list_filterByBookingIdAndStatus() throws Exception {
        // given (같은 결제에 실패한 취소 1건 + 성공한 취소 1건)
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        Long failedId = dataHelper.createCancellation(paymentAttemptId, ownerId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.FAILED, AMOUNT);
        dataHelper.createCancellation(paymentAttemptId, ownerId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.SUCCEEDED, AMOUNT);

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
        assertThat(longAt(body, "$.data.items[0].cancellationId")).isEqualTo(failedId);
        assertThat(longAt(body, "$.data.items[0].bookingId")).isEqualTo(bookingId);
        assertThat((String) JsonPath.read(body, "$.data.items[0].status")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("취소 내역: 기한 지난 예매의 실패한 취소도 관리자 목록에서는 재취소 가능(retryAllowed true)")
    void list_retryAllowed_adminRule() throws Exception {
        // given
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        dataHelper.createCancellation(paymentAttemptId, ownerId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.FAILED, AMOUNT);
        passCancellationDeadline(bookingId);

        // when
        String body = mvc.perform(get(URL).param("bookingId", bookingId.toString())
                        .header("Authorization", "Bearer " + accessTokenFor(adminId)))
                .andReturn().getResponse().getContentAsString();
        System.out.println("테스트: " + body);

        // then (회원 결과 조회였다면 기한이 지나 false)
        assertThat((Boolean) JsonPath.read(body, "$.data.items[0].retryAllowed")).isTrue();
    }

    @Test
    @DisplayName("취소 내역: 3건을 2개씩 나누면 0페이지 2건, 1페이지 1건, 전체 3건·2페이지")
    void list_paging() throws Exception {
        // given (실패 -> 실패 -> 성공 순으로 쌓인 취소)
        Long ownerId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = confirmedBooking(ownerId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        Long first = dataHelper.createCancellation(paymentAttemptId, ownerId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.FAILED, AMOUNT);
        Long second = dataHelper.createCancellation(paymentAttemptId, ownerId,
                CancellationInitiatorRole.MEMBER, CancellationStatus.FAILED, AMOUNT);
        Long third = dataHelper.createCancellation(paymentAttemptId, adminId,
                CancellationInitiatorRole.ADMIN, CancellationStatus.SUCCEEDED, AMOUNT);

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
        assertThat(longAt(page0, "$.data.items[0].cancellationId")).isEqualTo(third);
        assertThat(longAt(page0, "$.data.items[1].cancellationId")).isEqualTo(second);

        assertThat((List<?>) JsonPath.read(page1, "$.data.items")).hasSize(1);
        assertThat(longAt(page1, "$.data.items[0].cancellationId")).isEqualTo(first);
    }

    @Test
    @DisplayName("취소 내역: size 0, 없는 status는 400 VALIDATION_ERROR")
    void list_invalidParams_badRequest() throws Exception {
        // given
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        // when
        int zeroSize = mvc.perform(get(URL).param("size", "0")
                .header("Authorization", "Bearer " + accessTokenFor(adminId))).andReturn().getResponse().getStatus();
        ResultActions unknownStatus = mvc.perform(get(URL).param("status", "DONE")
                .header("Authorization", "Bearer " + accessTokenFor(adminId)));
        System.out.println("테스트: " + zeroSize + ", " + unknownStatus.andReturn().getResponse().getContentAsString());

        // then
        assertThat(zeroSize).isEqualTo(errorCode.getStatus().value());
        assertThat(unknownStatus.andReturn().getResponse().getStatus()).isEqualTo(errorCode.getStatus().value());
        assertThat((String) JsonPath.read(unknownStatus.andReturn().getResponse().getContentAsString(), "$.code"))
                .isEqualTo(errorCode.name());
    }

    @Test
    @DisplayName("취소 내역: MEMBER 토큰은 403")
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

    // 기한 전 확정 예매 (좌석 SOLD, 티켓 1장)
    private Long confirmedBooking(Long memberId) {
        return dataHelper.createBooking(memberId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
    }

    // 확정 예매 + 승인 결제 -> 결제 시도 ID
    private Long approvedAttempt(Long memberId) {
        return dataHelper.createPaymentAttempt(confirmedBooking(memberId), PaymentAttemptStatus.APPROVED, AMOUNT);
    }

    // 예매의 취소 기한을 과거로 (회원은 재취소 불가, 관리자는 가능한 상황)
    private void passCancellationDeadline(Long bookingId) {
        entityManager.createQuery("""
                        UPDATE Booking b
                        SET b.cancellationDeadlineSnapshot = :deadline
                        WHERE b.id = :bookingId
                        """)
                .setParameter("deadline", Instant.now().minus(1, ChronoUnit.HOURS))
                .setParameter("bookingId", bookingId)
                .executeUpdate();
        entityManager.clear();
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