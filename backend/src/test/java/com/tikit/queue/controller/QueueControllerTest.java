package com.tikit.queue.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.common.ErrorCode;
import com.tikit.queue.dto.response.QueueEntryResponseDTO;
import com.tikit.queue.entity.QueueEntryStatus;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.service.QueueResult;
import com.tikit.queue.service.QueueService;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class QueueControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final String ENTRY_ID = "22222222-2222-2222-2222-222222222222";
    private static final OffsetDateTime SERVER_TIME = OffsetDateTime.parse("2026-11-20T14:00:00+09:00");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    @MockitoBean
    private QueueService queueService;

    @Test
    @DisplayName("토큰 없이 줄을 서면 401")
    void join_withoutToken() throws Exception {
        mvc.perform(post("/api/v1/sessions/{sessionId}/queue-entries", SESSION_ID))
                .andExpect(status().isUnauthorized());

        then(queueService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("MANAGER는 대기열을 쓸 수 없어 403")
    void join_manager() throws Exception {
        mvc.perform(post("/api/v1/sessions/{sessionId}/queue-entries", SESSION_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MANAGER)))
                .andExpect(status().isForbidden());

        then(queueService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("새로 줄을 서면 201과 대기 순번")
    void join_created() throws Exception {
        given(queueService.join(MEMBER_ID, SESSION_ID)).willReturn(new QueueResult(waiting(), true));

        mvc.perform(post("/api/v1/sessions/{sessionId}/queue-entries", SESSION_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.queueEntryId").value(ENTRY_ID))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(12))
                .andExpect(jsonPath("$.data.waitingCount").value(40))
                .andExpect(jsonPath("$.data.admissionId").isEmpty())
                .andExpect(jsonPath("$.data.pollAfterSeconds").value(2));
    }

    @Test
    @DisplayName("이미 대기 중이면 200")
    void join_existing() throws Exception {
        given(queueService.join(MEMBER_ID, SESSION_ID)).willReturn(new QueueResult(waiting(), false));

        mvc.perform(post("/api/v1/sessions/{sessionId}/queue-entries", SESSION_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queueEntryId").value(ENTRY_ID));
    }

    @Test
    @DisplayName("판매 시작 전이면 409 SALE_NOT_OPEN")
    void join_saleNotOpen() throws Exception {
        given(queueService.join(MEMBER_ID, SESSION_ID)).willThrow(new QueueException(ErrorCode.SALE_NOT_OPEN));

        mvc.perform(post("/api/v1/sessions/{sessionId}/queue-entries", SESSION_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SALE_NOT_OPEN.name()));
    }

    @Test
    @DisplayName("회차 ID가 숫자가 아니면 400")
    void join_invalidSessionId() throws Exception {
        mvc.perform(post("/api/v1/sessions/{sessionId}/queue-entries", "abc")
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));
    }

    @Test
    @DisplayName("입장한 항목을 조회하면 입장 권한을 함께 준다")
    void get_admitted() throws Exception {
        QueueEntryResponseDTO admitted = new QueueEntryResponseDTO(ENTRY_ID, SESSION_ID, QueueEntryStatus.ADMITTED,
                0, 40, "33333333-3333-3333-3333-333333333333", SERVER_TIME.plusMinutes(10), 5, SERVER_TIME);
        given(queueService.get(MEMBER_ID, ENTRY_ID)).willReturn(admitted);

        mvc.perform(get("/api/v1/queue-entries/{queueEntryId}", ENTRY_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ADMITTED"))
                .andExpect(jsonPath("$.data.position").value(0))
                .andExpect(jsonPath("$.data.admissionId").value("33333333-3333-3333-3333-333333333333"))
                .andExpect(jsonPath("$.data.admissionExpiresAt").value("2026-11-20T14:10:00+09:00"));
    }

    @Test
    @DisplayName("본인 항목이 아니면 404")
    void get_notFound() throws Exception {
        given(queueService.get(MEMBER_ID, ENTRY_ID)).willThrow(new QueueException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/api/v1/queue-entries/{queueEntryId}", ENTRY_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("대기 취소는 본문 없이 204")
    void leave_noContent() throws Exception {
        mvc.perform(delete("/api/v1/queue-entries/{queueEntryId}", ENTRY_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(queueService).should().leave(MEMBER_ID, ENTRY_ID);
    }

    @Test
    @DisplayName("본인 항목이 아니면 대기 취소도 404")
    void leave_notFound() throws Exception {
        willThrow(new QueueException(ErrorCode.RESOURCE_NOT_FOUND)).given(queueService).leave(MEMBER_ID, ENTRY_ID);

        mvc.perform(delete("/api/v1/queue-entries/{queueEntryId}", ENTRY_ID)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isNotFound());
    }

    private QueueEntryResponseDTO waiting() {
        return new QueueEntryResponseDTO(ENTRY_ID, SESSION_ID, QueueEntryStatus.WAITING, 12, 40, null, null, 2,
                SERVER_TIME);
    }

    private String accessToken(Role role) {
        Member member = Member.create("queue-test@example.com", "encodedPassword", "tester", role);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return jwtProvider.generateAccessToken(member);
    }
}
