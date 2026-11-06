package com.tikit.admin.controller;

import com.tikit.admin.controller.advice.AdminManagerControllerAdvice;
import com.tikit.admin.entity.AuditLog;
import com.tikit.admin.repository.AuditLogRepository;
import com.tikit.admin.service.AdminManagerService;
import com.tikit.auth.controller.MemberController;
import com.tikit.auth.controller.advice.MemberControllerAdvice;
import com.tikit.auth.cookie.RefreshTokenCookie;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.service.CustomUserDetailsService;
import com.tikit.auth.service.MemberService;
import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.config.security.CustomAccessDeniedHandler;
import com.tikit.config.security.CustomAuthenticationEntryPoint;
import com.tikit.config.security.SecurityConfig;
import com.tikit.filter.JWTCheckFilter;
import com.tikit.member.controller.MemberProfileController;
import com.tikit.member.service.MemberProfileService;
import com.tikit.util.JWTProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 컨트롤러·서비스·인증·JWT·암호화를 연결하되 DB·Redis 저장소는 모킹합니다.
@WebMvcTest
@ContextConfiguration(classes = AdminManagerCreateApiTest.TestConfig.class)
@TestPropertySource(properties = "jwt.secret=manager-api-test-only-signing-key-2026-1234567890")
class AdminManagerCreateApiTest {

    @Configuration(proxyBeanMethods = false)
    @Import({AdminManagerController.class, AdminManagerService.class, AdminManagerControllerAdvice.class,
            GlobalExceptionHandler.class, SecurityConfig.class, JWTCheckFilter.class, JWTProvider.class,
            MemberController.class, MemberControllerAdvice.class, MemberService.class,
            CustomUserDetailsService.class, RefreshTokenCookie.class,
            MemberProfileController.class, MemberProfileService.class,
            CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
    static class TestConfig { }

    @Autowired private MockMvc mvc;
    @Autowired private JWTProvider jwtProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private MemberRepository memberRepository;
    @MockitoBean private AuditLogRepository auditLogRepository;
    @Autowired private UserDetailsService userDetailsService;
    @MockitoBean private RefreshTokenRepository refreshTokenRepository;

    private final List<Member> savedMembers = new ArrayList<>();
    private final List<AuditLog> savedAudits = new ArrayList<>();
    private static final String PASSWORD = "Example-password-2026!";
    private String adminToken;

    @BeforeEach
    void setUp() {
        Member admin = actor(Role.ADMIN, MemberStatus.ACTIVE);
        adminToken = jwtProvider.generateAccessToken(admin);
        when(memberRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return id.equals(1L) ? Optional.of(admin)
                    : savedMembers.stream().filter(member -> member.getId().equals(id)).findFirst();
        });
        when(memberRepository.findByEmail(anyString())).thenAnswer(invocation ->
                savedMembers.stream().filter(member -> member.getEmail().equals(invocation.getArgument(0))).findFirst());
        when(memberRepository.existsByEmail(any(String.class))).thenAnswer(invocation ->
                savedMembers.stream().anyMatch(member -> member.getEmail().equals(invocation.getArgument(0))));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 2L + savedMembers.size());
            ReflectionTestUtils.setField(member, "version", 0L);
            ReflectionTestUtils.setField(member, "createdAt", Instant.parse("2026-11-06T00:00:00Z"));
            savedMembers.add(member);
            return member;
        });
        when(auditLogRepository.saveAndFlush(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog audit = invocation.getArgument(0);
            savedAudits.add(audit);
            return audit;
        });
    }

    private Member actor(Role role, MemberStatus status) {
        Member actor = Member.create("admin@example.com", "unused-test-hash", "관리자", role);
        ReflectionTestUtils.setField(actor, "id", 1L);
        ReflectionTestUtils.setField(actor, "status", status);
        return actor;
    }

    private ResultActions create(String token, String body) throws Exception {
        var request = post("/api/v1/admin/managers").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    private String validBody(String email) {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "name", "운영 담당자"));
    }

    private void assertNothingSaved() {
        assertThat(savedMembers).isEmpty();
        assertThat(savedAudits).isEmpty();
        verify(memberRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("ACTIVE ADMIN은 정규화된 ACTIVE MANAGER 계정을 생성하고 감사 로그를 기록한다")
    void createManager() throws Exception {
        create(adminToken, validBody("  MANAGER@EXAMPLE.COM  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.memberId").value(2))
                .andExpect(jsonPath("$.data.email").value("manager@example.com"))
                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        assertThat(savedMembers).hasSize(1);
        Member manager = savedMembers.getFirst();
        assertThat(manager.getRole()).isEqualTo(Role.MANAGER);
        assertThat(manager.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(passwordEncoder.matches(PASSWORD, manager.getPasswordHash())).isTrue();
        assertThat(savedAudits).hasSize(1);
        assertThat(savedAudits.getFirst().getActorMemberId()).isEqualTo(1L);
        assertThat(savedAudits.getFirst().getTargetId()).isEqualTo(manager.getId());
        assertThat(savedAudits.getFirst().getAction()).isEqualTo("MANAGER_CREATE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"manager@example.com", "MANAGER@EXAMPLE.COM", "  Manager@Example.com  "})
    @DisplayName("같은 이메일을 다시 요청하면 공백·대소문자에 관계없이 409이며 추가 저장하지 않는다")
    void duplicateEmail(String email) throws Exception {
        create(adminToken, validBody("manager@example.com")).andExpect(status().isCreated());
        create(adminToken, validBody(email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        assertThat(savedMembers).hasSize(1);
        assertThat(savedAudits).hasSize(1);
        verify(memberRepository, times(1)).saveAndFlush(any());
        verify(auditLogRepository, times(1)).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"email\":\"invalid\"}",
            "{\"email\":\"manager@example.com\",\"password\":\"short\",\"name\":\"manager\"}"})
    @DisplayName("입력값이 잘못되면 400이며 계정과 감사 로그를 저장하지 않는다")
    void invalidInput(String body) throws Exception {
        create(adminToken, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertNothingSaved();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "MANAGER"})
    @DisplayName("실제 MEMBER·MANAGER 토큰으로는 403이며 서비스에 진입하지 않는다")
    void nonAdmin(String role) throws Exception {
        String token = jwtProvider.generateAccessToken(actor(Role.valueOf(role), MemberStatus.ACTIVE));
        create(token, validBody("manager@example.com")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(memberRepository, auditLogRepository);
    }

    @ParameterizedTest
    @CsvSource({"MEMBER,ACTIVE,ACCESS_DENIED", "MANAGER,ACTIVE,ACCESS_DENIED",
            "ADMIN,SUSPENDED,ACCOUNT_RESTRICTED", "ADMIN,WITHDRAWN,ACCOUNT_RESTRICTED"})
    @DisplayName("유효한 ADMIN 토큰도 DB의 현재 역할·상태가 변경되면 403이다")
    void rechecksCurrentActor(String role, String state, String code) throws Exception {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(actor(Role.valueOf(role), MemberStatus.valueOf(state))));
        create(adminToken, validBody("manager@example.com")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(code));
        assertNothingSaved();
    }

    @Test
    @DisplayName("DB에서 삭제된 ADMIN은 유효한 토큰이 있어도 생성할 수 없다")
    void missingAdmin() throws Exception {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        create(adminToken, validBody("manager@example.com")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertNothingSaved();
    }

    @Test
    @DisplayName("인증되지 않은 요청은 401이며 DB에 접근하지 않는다")
    void anonymous() throws Exception {
        create(null, validBody("manager@example.com")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(memberRepository, auditLogRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-token", "expired", "refresh"})
    @DisplayName("위조·만료·리프레시 토큰은 401이며 DB에 접근하지 않는다")
    void invalidToken(String type) throws Exception {
        String token = switch (type) {
            case "refresh" -> jwtProvider.generateRefreshToken(actor(Role.ADMIN, MemberStatus.ACTIVE));
            case "expired" -> Jwts.builder().subject("1").claim("roleName", "ADMIN").claim("tokenType", "ACCESS")
                    .expiration(Date.from(Instant.EPOCH)).signWith(Keys.hmacShaKeyFor(
                            "manager-api-test-only-signing-key-2026-1234567890".getBytes(StandardCharsets.UTF_8))).compact();
            default -> type;
        };
        create(token, validBody("manager@example.com")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(memberRepository, auditLogRepository);
    }

    @Test
    @DisplayName("감사 로그 저장 실패는 성공 응답으로 처리하지 않는다")
    void auditFailure() throws Exception {
        when(auditLogRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("audit failed"));
        create(adminToken, validBody("manager@example.com")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manager@example.com", "MANAGER@EXAMPLE.COM", "  Manager@Example.com  "})
    @DisplayName("생성한 MANAGER가 로그인하면 MANAGER 토큰·권한을 받고 관리자 API는 403이다")
    void createdManagerCanLoginWithManagerRole(String email) throws Exception {
        create(adminToken, validBody("  MANAGER@EXAMPLE.COM  ")).andExpect(status().isCreated());
        var response = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().httpOnly(RefreshTokenCookie.NAME, true))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();

        String accessToken = objectMapper.readTree(response.getContentAsString()).at("/data/accessToken").asString();
        var claims = jwtProvider.getClaims(accessToken);
        assertThat(claims.getSubject()).isEqualTo(savedMembers.getFirst().getId().toString());
        assertThat(claims.get("roleName", String.class)).isEqualTo("MANAGER");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
        assertThat(userDetailsService.loadUserByUsername(email).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_MANAGER");

        var refreshCookie = response.getCookie(RefreshTokenCookie.NAME);
        assertThat(refreshCookie).isNotNull();
        verify(refreshTokenRepository).save("2", refreshCookie.getValue());
        assertThat(jwtProvider.getClaims(refreshCookie.getValue()).getSubject()).isEqualTo("2");
        assertThat(jwtProvider.getClaims(refreshCookie.getValue()).get("tokenType", String.class)).isEqualTo("REFRESH");

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.email").value("manager@example.com"));
        create(accessToken, validBody("another-manager@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(savedMembers).hasSize(1);
        assertThat(savedAudits).hasSize(1);
    }

    @Test
    @DisplayName("생성한 MANAGER의 비밀번호가 틀리면 401이며 세션을 저장하지 않는다")
    void createdManagerWrongPassword() throws Exception {
        create(adminToken, validBody("manager@example.com")).andExpect(status().isCreated());
        login("manager@example.com", "Wrong-password-2026!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(refreshTokenRepository);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    @DisplayName("생성 후 정지·탈퇴된 MANAGER는 올바른 비밀번호로도 로그인할 수 없다")
    void createdInactiveManagerCannotLogin(MemberStatus status) throws Exception {
        create(adminToken, validBody("manager@example.com")).andExpect(status().isCreated());
        ReflectionTestUtils.setField(savedMembers.getFirst(), "status", status);
        login("manager@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("생성한 MANAGER의 토큰 재발급에도 현재 MANAGER 역할이 반영된다")
    void createdManagerRefreshPreservesRole() throws Exception {
        create(adminToken, validBody("manager@example.com")).andExpect(status().isCreated());
        var loginResponse = login("manager@example.com", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        var refreshCookie = loginResponse.getCookie(RefreshTokenCookie.NAME);
        assertThat(refreshCookie).isNotNull();
        when(refreshTokenRepository.rotate(eq("2"), eq(refreshCookie.getValue()), anyString())).thenReturn(1L);

        var response = mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(cookie().httpOnly(RefreshTokenCookie.NAME, true))
                .andReturn().getResponse();
        String token = objectMapper.readTree(response.getContentAsString()).at("/data/accessToken").asString();
        assertThat(jwtProvider.getClaims(token).get("roleName", String.class)).isEqualTo("MANAGER");
        assertThat(jwtProvider.getClaims(token).getSubject()).isEqualTo("2");
        verify(refreshTokenRepository).rotate(eq("2"), eq(refreshCookie.getValue()), anyString());
    }
}
