package com.tikit.admin.controller;

import com.tikit.admin.controller.advice.AdminManagerControllerAdvice;
import com.tikit.admin.dto.request.AdminManagerCreateRequestDTO;
import com.tikit.admin.dto.response.AdminManagerCreateResponseDTO;
import com.tikit.admin.service.AdminManagerService;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.exception.AccountRestrictedException;
import com.tikit.auth.exception.DuplicateEmailException;
import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.common.ErrorCode;
import com.tikit.config.security.CustomAccessDeniedHandler;
import com.tikit.config.security.CustomAuthenticationEntryPoint;
import com.tikit.config.security.SecurityConfig;
import com.tikit.filter.JWTCheckFilter;
import com.tikit.util.JWTProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;

import java.time.Instant;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = AdminManagerControllerTest.TestConfig.class)
class AdminManagerControllerTest {

    @Configuration(proxyBeanMethods = false)
    @Import({AdminManagerController.class, AdminManagerControllerAdvice.class, GlobalExceptionHandler.class,
            SecurityConfig.class, JWTCheckFilter.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
    static class TestConfig { }

    @Autowired private MockMvc mvc;
    @MockitoBean private AdminManagerService service;
    @MockitoBean private JWTProvider jwtProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    private static final String URL = "/api/v1/admin/managers";
    private static final String BODY = """
            {"email":"manager@example.com","password":"Example-password-2026!","name":"운영 담당자"}
            """;

    private void token(String role) {
        when(jwtProvider.getClaims("test-token")).thenReturn(Jwts.claims().subject("1")
                .add("roleName", role).add("tokenType", "ACCESS").build());
    }

    @Test
    void adminCreatesManagerWith201AndNoPassword() throws Exception {
        token("ADMIN");
        when(service.createManager(eq(1L), any())).thenReturn(new AdminManagerCreateResponseDTO(
                2L, "manager@example.com", "운영 담당자", Role.MANAGER, MemberStatus.ACTIVE,
                0L, Instant.parse("2026-11-06T00:00:00Z")));
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.memberId").value(2))
                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(service).createManager(eq(1L), argThat((AdminManagerCreateRequestDTO request) ->
                "manager@example.com".equals(request.getEmail())
                        && "Example-password-2026!".equals(request.getPassword())
                        && "운영 담당자".equals(request.getName())));
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "MANAGER"})
    void nonAdminGets403(String role) throws Exception {
        token(role);
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidBodyGets400(String body) throws Exception {
        token("ADMIN");
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(service);
    }

    static Stream<String> invalidBodies() {
        return Stream.of(
            "{}",
            "{\"email\":\"invalid\",\"password\":\"Example-password-2026!\",\"name\":\"운영 담당자\"}",
            "{\"email\":\"manager@example.com\",\"password\":\"short\",\"name\":\"운영 담당자\"}",
            "{\"email\":\"manager@example.com\",\"password\":\"Example-password-2026!\",\"name\":\" \"}",
            "{",
            body(null, "Example-password-2026!", "manager"),
            body("", "Example-password-2026!", "manager"),
            body("   ", "Example-password-2026!", "manager"),
            body("manager @example.com", "Example-password-2026!", "manager"),
            body(emailOfLength(255), "Example-password-2026!", "manager"),
            body("manager@example.com", null, "manager"),
            body("manager@example.com", "", "manager"),
            body("manager@example.com", " ".repeat(10), "manager"),
            body("manager@example.com", "a".repeat(9), "manager"),
            body("manager@example.com", "a".repeat(129), "manager"),
            body("manager@example.com", "Example-password-2026!", null),
            body("manager@example.com", "Example-password-2026!", ""),
            body("manager@example.com", "Example-password-2026!", "a".repeat(51))
        );
    }

    static String emailOfLength(int length) {
        return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(length - 193);
    }

    static String body(String email, String password, String name) {
        return "{\"email\":%s,\"password\":%s,\"name\":%s}".formatted(
                jsonString(email), jsonString(password), jsonString(name));
    }

    static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void normalizesEmailBeforeValidation() throws Exception {
        token("ADMIN");
        String body = body("  MANAGER@EXAMPLE.COM  ", "Example-password-2026!", "manager");
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(service).createManager(eq(1L), argThat(request -> "manager@example.com".equals(request.getEmail())));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 128})
    void acceptsLengthBoundaries(int passwordLength) throws Exception {
        token("ADMIN");
        String body = body("  " + emailOfLength(254) + "  ", "a".repeat(passwordLength), "a".repeat(50));
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(service).createManager(eq(1L), argThat(request ->
                request.getEmail().length() == 254 && request.getPassword().length() == passwordLength
                        && request.getName().length() == 50));
    }

    @Test
    void normalizedDuplicateEmailGets409() throws Exception {
        token("ADMIN");
        when(service.createManager(eq(1L), argThat(request ->
                request != null && "manager@example.com".equals(request.getEmail()))))
                .thenThrow(new DuplicateEmailException("duplicate"));
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  MANAGER@EXAMPLE.COM  ", "Example-password-2026!", "manager")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void duplicateGets409() throws Exception {
        token("ADMIN");
        when(service.createManager(eq(1L), any())).thenThrow(new DuplicateEmailException("duplicate"));
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void inactiveAdminGets403() throws Exception {
        token("ADMIN");
        when(service.createManager(eq(1L), any())).thenThrow(new AccountRestrictedException("inactive"));
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
    }

    @Test
    void changedRoleGets403() throws Exception {
        token("ADMIN");
        when(service.createManager(eq(1L), any())).thenThrow(new AccessDeniedException("changed role"));
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @MethodSource("databaseFailures")
    void databaseFailureGetsStandard500WithoutInternalDetails(RuntimeException failure) throws Exception {
        token("ADMIN");
        when(service.createManager(eq(1L), any())).thenThrow(failure);
        mvc.perform(post(URL).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(content().string(not(containsString("private-sql-details"))))
                .andExpect(content().string(not(containsString("Example-password-2026!"))));
    }

    static Stream<RuntimeException> databaseFailures() {
        return Stream.of(
                new DataIntegrityViolationException("private-sql-details Example-password-2026!"),
                new DataAccessResourceFailureException("private-sql-details Example-password-2026!"),
                new TransactionSystemException("private-sql-details Example-password-2026!")
        );
    }
}
