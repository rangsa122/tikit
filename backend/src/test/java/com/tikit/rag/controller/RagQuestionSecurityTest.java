package com.tikit.rag.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.config.security.CustomAccessDeniedHandler;
import com.tikit.config.security.CustomAuthenticationEntryPoint;
import com.tikit.config.security.SecurityConfig;
import com.tikit.filter.JWTCheckFilter;
import com.tikit.rag.controller.advice.RagQuestionControllerAdvice;
import com.tikit.rag.dto.response.RagQuestionResponse;
import com.tikit.rag.dto.response.RagSource;
import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.service.RagQuestionAnswerer;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 보안 필터를 통과하는 화면 연동 요청을 DB와 모델 서버 없이 검증한다. */
@SpringJUnitWebConfig(RagQuestionSecurityTest.Config.class)
@TestPropertySource(properties = "jwt.secret=rag-question-test-only-secret-key-1234567890")
class RagQuestionSecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired JWTProvider jwtProvider;
    @Autowired RagQuestionAnswerer answerer;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(answerer);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousUsersCanReadAnswerAndSourceTitle() throws Exception {
        when(answerer.answer("취소 수수료는?")).thenReturn(response());
        mvc.perform(question())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("취소 가능한 경우 수수료는 0원입니다."))
                .andExpect(jsonPath("$.data.sources[0].documentTitle").value("취소 및 환불 안내"));
    }

    @Test
    void rejectsInvalidRefreshAndServiceTokensFromAppClients() throws Exception {
        for (var token : List.of("invalid-token", jwtProvider.generateRefreshToken(member(Role.MEMBER)),
                jwtProvider.generateRagServiceToken())) {
            mvc.perform(question().header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
        verifyNoInteractions(answerer);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void loggedInUsersCanReadAnswerAndSourceTitle(Role role) throws Exception {
        when(answerer.answer("취소 수수료는?")).thenReturn(response());
        mvc.perform(question().header("Authorization", "Bearer " + jwtProvider.generateAccessToken(member(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.question").value("취소 수수료는?"))
                .andExpect(jsonPath("$.data.answer").value("취소 가능한 경우 수수료는 0원입니다."))
                .andExpect(jsonPath("$.data.sources[0].documentTitle").value("취소 및 환불 안내"));
    }

    @Test
    void sameQuestionCanBeRetriedAfterUnavailableResponse() throws Exception {
        when(answerer.answer("취소 수수료는?")).thenThrow(new RagSearchUnavailableException())
                .thenReturn(response());
        var token = jwtProvider.generateAccessToken(member(Role.MEMBER));

        mvc.perform(question().header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(question().header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources[0].documentTitle").value("취소 및 환불 안내"));
    }

    private MockHttpServletRequestBuilder question() {
        return post("/api/v1/rag/questions").contentType("application/json")
                .content("{\"question\":\"취소 수수료는?\"}");
    }

    private RagQuestionResponse response() {
        return new RagQuestionResponse("취소 수수료는?", "취소 가능한 경우 수수료는 0원입니다.",
                List.of(new RagSource("refund", "취소 및 환불 안내")), List.of());
    }

    private Member member(Role role) {
        var member = Member.create("rag-test@example.com", "unused", "tester", role);
        ReflectionTestUtils.setField(member, "id", 42L);
        return member;
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JWTProvider.class, JWTCheckFilter.class,
            CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
            RagQuestionController.class, RagQuestionControllerAdvice.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean RagQuestionAnswerer ragQuestionAnswerer() { return mock(RagQuestionAnswerer.class); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
