package com.tikit.booking.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.dto.response.TicketListResponseDTO;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.service.BookingQueryService;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.PageResponseDTO;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BookingQueryControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    @MockitoBean
    private BookingQueryService bookingQueryService;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/me/bookings", "/api/v1/bookings/100", "/api/v1/bookings/100/tickets"})
    @DisplayName("토큰 없이 조회하면 401")
    void withoutToken(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.name()));

        then(bookingQueryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/me/bookings", "/api/v1/bookings/100", "/api/v1/bookings/100/tickets"})
    @DisplayName("일반 회원이 아니면 403")
    void managerForbidden(String url) throws Exception {
        mvc.perform(get(url).header("Authorization", "Bearer " + accessToken(Role.MANAGER)))
                .andExpect(status().isForbidden());

        then(bookingQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내 예매 목록은 기본 page 0, size 20으로 조회한다")
    void getMyBookings_defaults() throws Exception {
        given(bookingQueryService.getMyBookings(eq(MEMBER_ID), isNull(), eq(0), eq(20)))
                .willReturn(PageResponseDTO.from(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1)));

        mvc.perform(get("/api/v1/me/bookings").header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].bookingId").value(100))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @DisplayName("page, size, status를 서비스에 넘긴다")
    void getMyBookings_params() throws Exception {
        given(bookingQueryService.getMyBookings(MEMBER_ID, BookingStatus.CONFIRMED, 2, 100))
                .willReturn(PageResponseDTO.from(new PageImpl<>(List.of(), PageRequest.of(2, 100), 0)));

        mvc.perform(get("/api/v1/me/bookings")
                        .param("page", "2")
                        .param("size", "100")
                        .param("status", "CONFIRMED")
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isOk());

        then(bookingQueryService).should().getMyBookings(MEMBER_ID, BookingStatus.CONFIRMED, 2, 100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "status=UNKNOWN", "page=abc"})
    @DisplayName("잘못된 목록 조건은 400")
    void getMyBookings_invalidParams(String query) throws Exception {
        mvc.perform(get("/api/v1/me/bookings?" + query)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));

        then(bookingQueryService).should(never()).getMyBookings(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("예매 상세를 조회한다")
    void getBooking() throws Exception {
        given(bookingQueryService.getBooking(MEMBER_ID, 100L)).willReturn(response());

        mvc.perform(get("/api/v1/bookings/100").header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingId").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("다른 회원 예매는 404")
    void getBooking_notFound() throws Exception {
        given(bookingQueryService.getBooking(MEMBER_ID, 100L))
                .willThrow(new BookingException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/api/v1/bookings/100").header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("숫자가 아닌 bookingId는 400")
    void getBooking_invalidId() throws Exception {
        mvc.perform(get("/api/v1/bookings/abc").header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));
    }

    @Test
    @DisplayName("예매 티켓을 조회한다")
    void getTickets() throws Exception {
        given(bookingQueryService.getTickets(MEMBER_ID, 100L)).willReturn(new TicketListResponseDTO(List.of(
                new TicketListResponseDTO.TicketItem(51L, 100L, 201L, "A열 1번", "테스트 공연", null, TicketStatus.VALID))));

        mvc.perform(get("/api/v1/bookings/100/tickets")
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ticketId").value(51))
                .andExpect(jsonPath("$.data.items[0].seatLabel").value("A열 1번"))
                .andExpect(jsonPath("$.data.items[0].status").value("VALID"));
    }

    private String accessToken(Role role) {
        Member member = Member.create("booking-query-test@example.com", "encodedPassword", "tester", role);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return jwtProvider.generateAccessToken(member);
    }

    private BookingResponseDTO response() {
        return new BookingResponseDTO(100L, 10L, 5L, "테스트 공연", null, BookingStatus.PENDING_PAYMENT,
                List.of(), 250_000L, null, null, null, null, null, null, null);
    }
}
