package com.tikit.rag.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.config.security.CustomAccessDeniedHandler;
import com.tikit.config.security.CustomAuthenticationEntryPoint;
import com.tikit.config.security.SecurityConfig;
import com.tikit.filter.JWTCheckFilter;
import com.tikit.rag.controller.advice.RagControllerAdvice;
import com.tikit.rag.dto.response.RagDocumentDetailResponse;
import com.tikit.rag.entity.RagDocumentType;
import com.tikit.rag.entity.RagIndexStatus;
import com.tikit.rag.service.RagDocumentService;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the production security chain, JWT signature validation and method authorization. */
@SpringJUnitWebConfig(AdminRagDocumentSecurityTest.Config.class)
@TestPropertySource(properties = "jwt.secret=rag-security-test-only-secret-key-1234567890")
class AdminRagDocumentSecurityTest {
    private static final String URL = "/api/v1/admin/rag/documents";
    @Autowired WebApplicationContext context;
    @Autowired JWTProvider jwtProvider;
    @Autowired RagDocumentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("미인증 사용자는 문서 등록과 목록 및 상세 조회 시 401을 받는다")
    void anonymousReceives401ForAllThreeEndpoints() throws Exception {
        for (var request : requests()) {
            mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("일반 회원의 JWT로 문서 등록과 목록 및 상세 조회를 요청하면 403을 반환한다")
    void memberJwtReceives403ForAllThreeEndpoints() throws Exception {
        String token = jwtProvider.generateAccessToken(member(Role.MEMBER));
        for (var request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("공연 관리자의 JWT도 공통 정책 문서 API에서는 403을 반환한다")
    void managerJwtReceives403ForAllThreeEndpoints() throws Exception {
        String token = jwtProvider.generateAccessToken(member(Role.MANAGER));
        for (var request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("관리자의 JWT로 문서를 등록하고 목록과 상세를 조회할 수 있다")
    void adminJwtCanCreateListAndReadDetail() throws Exception {
        var detail = new RagDocumentDetailResponse(1L, RagDocumentType.POLICY, "Policy", "Content",
                1, null, RagIndexStatus.QUEUED, 42L, Instant.now(), Instant.now());
        when(service.createPolicy(eq(42L), any())).thenReturn(detail);
        when(service.getDocuments(any())).thenReturn(Page.empty());
        when(service.getDocument(1L)).thenReturn(detail);
        String token = jwtProvider.generateAccessToken(member(Role.ADMIN));

        mvc.perform(createRequest().header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", URL + "/1"))
                .andExpect(jsonPath("$.data.createdBy").value(42));
        mvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        mvc.perform(get(URL + "/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("Content"));
        verify(service).createPolicy(eq(42L), any());
        verify(service).getDocuments(any());
        verify(service).getDocument(1L);
    }

    @Test
    @DisplayName("유효하지 않은 JWT는 401을 반환하고 서비스를 호출하지 않는다")
    void invalidJwtReceives401WithoutCallingService() throws Exception {
        for (var request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("리프레시 토큰으로 관리자 문서 API에 접근하면 401을 반환한다")
    void refreshTokenCannotAccessAdminEndpoints() throws Exception {
        String token = jwtProvider.generateRefreshToken(member(Role.ADMIN));
        for (var request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
        verifyNoInteractions(service);
    }

    private List<MockHttpServletRequestBuilder> requests() {
        return List.of(createRequest(), get(URL), get(URL + "/1"));
    }

    private MockHttpServletRequestBuilder createRequest() {
        return post(URL).contentType("application/json")
                .content("{\"title\":\"Policy\",\"content\":\"Content\"}");
    }

    private Member member(Role role) {
        Member member = Member.create("security-test@example.com", "unused", "tester", role);
        ReflectionTestUtils.setField(member, "id", 42L);
        return member;
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JWTProvider.class, JWTCheckFilter.class,
            CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
            AdminRagDocumentController.class, RagControllerAdvice.class})
    static class Config {
        @Bean RagDocumentService ragDocumentService() { return mock(RagDocumentService.class); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
