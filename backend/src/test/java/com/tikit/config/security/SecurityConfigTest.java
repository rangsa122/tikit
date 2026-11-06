package com.tikit.config.security;

import com.tikit.auth.dto.request.MemberLoginRequestDTO;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

@ActiveProfiles("local")
@AutoConfigureMockMvc // Mock(가짜) 환경에 MockMvc가 등록
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class SecurityConfigTest {

    // 가짜 환경에 등록된 MockMvc를 주입
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MemberRepository memberRepository;


    @Test
    @DisplayName("로그인 요청 body가 없느면 400을 반환")
    void authentication_test1() throws Exception {
        // given

        // when
        ResultActions resultActions = mvc.perform(post("/api/v1/auth/login"));
        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(400);
    }

    @Test
    @Transactional
    @DisplayName("잘못된 로그인 정보를 보내면 401 반환")
    void authentication_test2() throws Exception {
        // given
        Member member = Member.create(
                "test12345@gmail.com",
                passwordEncoder.encode("1234"),
                "test",
                Role.MEMBER
        );

        // 저장 후
        memberRepository.save(member);

        // 바로 insert
        memberRepository.flush();

        MemberLoginRequestDTO dto = new MemberLoginRequestDTO(
                "test1234567error@gmail.com",
                "1234"
        );

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(401);
        resultActions.andExpect(
                jsonPath("$.code").value("INVALID_CREDENTIALS")
        );
    }


    @Test
    @Transactional
    @DisplayName("올바른 로그인 정보를 보내면 200과 JWT 토큰을 반환")
    void authentication_test3() throws Exception {
        // given
        Member member = Member.create(
                "test12345@gmail.com",
                passwordEncoder.encode("1234"),
                "test",
                Role.MEMBER
        );

        // 저장 후
        memberRepository.save(member);

        // 바로 insert
        memberRepository.flush();

        MemberLoginRequestDTO dto = new MemberLoginRequestDTO(
                "test12345@gmail.com",
                "1234"
        );

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
        );

        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();

        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(200);

        resultActions
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"));
    }


    @Test
    void authorization_test() throws Exception {
        // given

        // when
        ResultActions resultActions = mvc.perform(post("/api/v1/auth/login"));
        String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        int httpStatusCode = resultActions.andReturn().getResponse().getStatus();
        System.out.println("테스트: " + responseBody);
        System.out.println("테스트: " + httpStatusCode);

        // then
        assertThat(httpStatusCode).isEqualTo(400);
    }


}
