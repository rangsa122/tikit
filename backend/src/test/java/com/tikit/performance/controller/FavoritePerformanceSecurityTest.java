
package com.tikit.performance.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.auth.service.CustomUserDetailsService;
import com.tikit.config.security.CustomAccessDeniedHandler;
import com.tikit.config.security.CustomAuthenticationEntryPoint;
import com.tikit.config.security.SecurityConfig;
import com.tikit.filter.JWTCheckFilter;
import com.tikit.performance.controller.advice.PerformanceControllerAdvice;
import com.tikit.performance.service.FavoritePerformanceService;
import com.tikit.performance.service.PerformanceService;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(
        classes = FavoritePerformanceSecurityTest.TestConfig.class
)
@TestPropertySource(
        properties = "jwt.secret=favorite-security-test-only-signing-key-2026-1234567890"
)
class FavoritePerformanceSecurityTest {

    @Configuration(proxyBeanMethods = false)
    @Import({
            FavoritePerformanceController.class,
            PublicPerformanceController.class,
            PerformanceControllerAdvice.class,
            SecurityConfig.class,
            JWTCheckFilter.class,
            JWTProvider.class,
            CustomUserDetailsService.class,
            CustomAuthenticationEntryPoint.class,
            CustomAccessDeniedHandler.class
    })
    static class TestConfig {
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private FavoritePerformanceService favoritePerformanceService;

    @MockitoBean
    private PerformanceService performanceService;

    private String memberToken;

    @BeforeEach
    void setUp() {
        Member member = Member.create(
                "favorite-test@example.com",
                "unused-test-password-hash",
                "테스트 회원",
                Role.MEMBER
        );

        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.ACTIVE
        );

        memberToken = jwtProvider.generateAccessToken(member);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
    }

    @Test
    @DisplayName("JWT 없이 관심 공연 등록 요청 시 401")
    void addFavoriteWithoutToken() throws Exception {
        mvc.perform(put("/api/v1/me/favorites/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    @DisplayName("JWT 없이 관심 공연 삭제 요청 시 401")
    void removeFavoriteWithoutToken() throws Exception {
        mvc.perform(delete("/api/v1/me/favorites/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    @DisplayName("잘못된 JWT로 관심 공연 등록 요청 시 401")
    void addFavoriteWithInvalidToken() throws Exception {
        mvc.perform(
                        put("/api/v1/me/favorites/10")
                                .header("Authorization", "Bearer invalid-token")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    @DisplayName("정상 JWT로 관심 공연 등록 시 서비스 호출")
    void addFavoriteWithValidToken() throws Exception {
        mvc.perform(
                        put("/api/v1/me/favorites/10")
                                .header("Authorization", "Bearer " + memberToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(favoritePerformanceService)
                .addFavorite(1L, 10L);
    }

    @Test
    @DisplayName("정상 JWT로 관심 공연 삭제 시 204")
    void removeFavoriteWithValidToken() throws Exception {
        mvc.perform(
                        delete("/api/v1/me/favorites/10")
                                .header("Authorization", "Bearer " + memberToken)
                )
                .andExpect(status().isNoContent());

        verify(favoritePerformanceService)
                .removeFavorite(1L, 10L);
    }

    @Test
    @DisplayName("공개 공연 상세 조회는 JWT 없이 접근 가능")
    void publicPerformanceDetailWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/performances/10"))
                .andExpect(status().isOk());

        verify(performanceService).getPublicDetail(10L);
    }

    @Test
    @DisplayName("공개 공연 회차 조회는 JWT 없이 접근 가능")
    void publicPerformanceSessionsWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/performances/10/sessions"))
                .andExpect(status().isOk());

        verify(performanceService).getPublicSessions(10L);
    }
}
