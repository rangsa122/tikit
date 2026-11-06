package com.tikit.auth.controller;

import com.tikit.auth.dto.request.MemberLoginRequestDTO;
import com.tikit.auth.dto.request.MemberRegisterRequestDTO;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class MemberAuthApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JWTProvider jwtProvider;

    private static final String PASSWORD = "Example-password-2026!";

    // 테스트용 회원 저장
    private Member saveMember(String email, Role role, MemberStatus status) {
        Member member = Member.create(email, passwordEncoder.encode(PASSWORD), "테스트", role);
        ReflectionTestUtils.setField(member, "status", status);
        if (status == MemberStatus.WITHDRAWN) {
            ReflectionTestUtils.setField(member, "withdrawnAt", java.time.Instant.now());
        }
        return memberRepository.saveAndFlush(member);
    }

    @Test
    @DisplayName("비밀번호가 10자 미만이면 회원가입 400 VALIDATION_ERROR")
    void signup_shortPassword() throws Exception {
        MemberRegisterRequestDTO dto = new MemberRegisterRequestDTO("signup-test@example.com", "short", "테스트");

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("가입되지 않은 이메일은 로그인 없이 중복 확인하면 available true")
    void emailAvailability_newEmail() throws Exception {
        mvc.perform(get("/api/v1/auth/email-availability")
                        .param("email", "new-email-test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("대소문자만 다른 가입 이메일은 중복 확인하면 available false")
    void emailAvailability_registeredEmail() throws Exception {
        saveMember("taken-email-test@example.com", Role.MEMBER, MemberStatus.ACTIVE);

        mvc.perform(get("/api/v1/auth/email-availability")
                        .param("email", "Taken-Email-Test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("탈퇴 회원의 이메일도 중복 확인하면 available false")
    void emailAvailability_withdrawnMember() throws Exception {
        saveMember("withdrawn-email-test@example.com", Role.MEMBER, MemberStatus.WITHDRAWN);

        mvc.perform(get("/api/v1/auth/email-availability")
                        .param("email", "withdrawn-email-test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 중복 확인 400 VALIDATION_ERROR")
    void emailAvailability_invalidEmail() throws Exception {
        mvc.perform(get("/api/v1/auth/email-availability")
                        .param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("이메일 없이 중복 확인하면 400 VALIDATION_ERROR")
    void emailAvailability_missingEmail() throws Exception {
        mvc.perform(get("/api/v1/auth/email-availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 회원가입 409 EMAIL_ALREADY_EXISTS")
    void signup_duplicateEmail() throws Exception {
        saveMember("duplicate-test@example.com", Role.MEMBER, MemberStatus.ACTIVE);
        MemberRegisterRequestDTO dto = new MemberRegisterRequestDTO("Duplicate-Test@example.com", PASSWORD, "테스트");

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("정지 계정은 올바른 비밀번호로 로그인해도 403 ACCOUNT_RESTRICTED")
    void login_suspendedMember() throws Exception {
        saveMember("suspended-test@example.com", Role.MEMBER, MemberStatus.SUSPENDED);
        MemberLoginRequestDTO dto = new MemberLoginRequestDTO("suspended-test@example.com", PASSWORD);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
    }

    @Test
    @DisplayName("MANAGER 토큰으로 내 정보 조회 200")
    void getMe_manager() throws Exception {
        Member manager = saveMember("manager-test@example.com", Role.MANAGER, MemberStatus.ACTIVE);
        String accessToken = jwtProvider.generateAccessToken(manager);

        mvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("manager-test@example.com"));
    }
}
