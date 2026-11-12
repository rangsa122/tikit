package com.tikit.admin.controller;

import com.tikit.admin.dto.response.AdminMemberSummaryResponseDTO;
import com.tikit.admin.service.AdminMemberService;
import com.tikit.auth.entity.*;
import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.common.ErrorCode;
import com.tikit.config.security.*;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminMemberController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class AdminMemberRoleControllerTest {
    private static final String URL = "/api/v1/admin/members/2/role";
    private static final String BODY = """
            {"role":"MANAGER","expectedVersion":3,"reason":"공연 운영 담당"}
            """;
    @Autowired MockMvc mvc;
    @MockitoBean AdminMemberService service;
    @MockitoBean JWTProvider jwtProvider;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean com.tikit.auth.cookie.RefreshTokenCookie refreshTokenCookie;
    @MockitoBean org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "1", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void returnsUpdatedMember() throws Exception {
        when(service.changeRole(eq(1L), eq(2L), any())).thenReturn(new AdminMemberSummaryResponseDTO(
                2L, "member@example.com", "회원", Role.MANAGER, MemberStatus.ACTIVE, 4L));
        mvc.perform(patch(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.version").value(4));
    }

    @Test
    void acceptsMemberRoleToDemoteManager() throws Exception {
        when(service.changeRole(eq(1L), eq(2L), any())).thenReturn(new AdminMemberSummaryResponseDTO(
                2L, "member@example.com", "회원", Role.MEMBER, MemberStatus.ACTIVE, 4L));
        mvc.perform(patch(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("MANAGER", "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.version").value(4));
        verify(service).changeRole(eq(1L), eq(2L), argThat(request -> request.role() == Role.MEMBER));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "MANAGER"})
    void requiresAdmin(String role) throws Exception {
        mvc.perform(patch(URL).with(user("1").roles(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\":\"ADMIN\",\"expectedVersion\":3,\"reason\":\"사유\"}",
            "{\"role\":\"UNKNOWN\",\"expectedVersion\":3,\"reason\":\"사유\"}",
            "{\"role\":\"MANAGER\",\"expectedVersion\":-1,\"reason\":\"사유\"}",
            "{\"role\":\"MANAGER\",\"expectedVersion\":3,\"reason\":\" \"}",
            "{\"role\":\"MANAGER\",\"reason\":\"사유\"}"})
    void rejectsInvalidRequest(String body) throws Exception {
        mvc.perform(patch(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidMemberId() throws Exception {
        mvc.perform(patch("/api/v1/admin/members/0/role").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void staleVersionReturnsConflict() throws Exception {
        when(service.changeRole(anyLong(), anyLong(), any())).thenThrow(new MemberStatusChangeException(ErrorCode.VERSION_CONFLICT));
        mvc.perform(patch(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void concurrentUpdateReturnsConflict() throws Exception {
        when(service.changeRole(anyLong(), anyLong(), any())).thenThrow(new ObjectOptimisticLockingFailureException(Member.class, 2L));
        mvc.perform(patch(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void detailIncludesSuspensionMetadata() throws Exception {
        Member member = Member.create("member@example.com", "hash", "회원", Role.MEMBER);
        member.suspend();
        var log = com.tikit.admin.entity.AuditLog.memberStatusChange(
                1L, 2L, "운영 정책 위반", MemberStatus.ACTIVE, MemberStatus.SUSPENDED);
        org.springframework.test.util.ReflectionTestUtils.setField(log, "createdAt", java.time.Instant.parse("2026-11-06T01:00:00Z"));
        when(service.getMember(2L)).thenReturn(com.tikit.admin.dto.response.AdminMemberDetailResponseDTO.from(member, log));
        mvc.perform(get("/api/v1/admin/members/2").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspensionReason").value("운영 정책 위반"))
                .andExpect(jsonPath("$.data.suspendedAt").value("2026-11-06T01:00:00Z"))
                .andExpect(jsonPath("$.data.suspendedBy").value(1))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void detailWithoutCurrentSuspensionHasNullMetadata() throws Exception {
        Member member = Member.create("member@example.com", "hash", "회원", Role.MEMBER);
        when(service.getMember(2L)).thenReturn(com.tikit.admin.dto.response.AdminMemberDetailResponseDTO.from(member));
        mvc.perform(get("/api/v1/admin/members/2").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspensionReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.suspendedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.suspendedBy").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "MANAGER"})
    void detailReasonIsAdminOnly(String role) throws Exception {
        mvc.perform(get("/api/v1/admin/members/2").with(user("1").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void creationEndpointIsRemoved() throws Exception {
        mvc.perform(post("/api/v1/admin/members/managers").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
