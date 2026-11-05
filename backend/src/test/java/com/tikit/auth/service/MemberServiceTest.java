package com.tikit.auth.service;

import com.tikit.auth.dto.request.MemberLoginRequestDTO;
import com.tikit.auth.dto.request.MemberRegisterRequestDTO;
import com.tikit.auth.dto.response.JwtTokenResponseDTO;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.exception.AccountRestrictedException;
import com.tikit.auth.exception.DuplicateEmailException;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.config.security.CustomUserDetails;
import com.tikit.util.JWTProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JWTProvider jwtProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private MemberService memberService;

    // 상태를 지정한 회원 생성
    private Member member(MemberStatus status) {
        Member member = Member.create("test@example.com", "encodedPassword", "김회원", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    // 비밀번호 검증을 통과한 인증 결과 설정
    private void givenAuthenticated(Member member) {
        CustomUserDetails userDetails = new CustomUserDetails(member);
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities())
        );
    }

    // 리프레시 토큰 검증을 통과한 클레임 설정
    private void givenRefreshClaims(String refreshToken) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("tokenType", String.class)).thenReturn("REFRESH");
        when(claims.getSubject()).thenReturn("1");
        when(jwtProvider.getClaims(refreshToken)).thenReturn(claims);
    }

    private MemberRegisterRequestDTO signupRequest(String email) {
        return new MemberRegisterRequestDTO(email, "Example-password-2026!", "김회원");
    }

    @Test
    @DisplayName("활성 계정은 로그인하면 토큰을 발급하고 리프레시 토큰을 저장")
    void login_activeMember() {
        givenAuthenticated(member(MemberStatus.ACTIVE));
        when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");

        JwtTokenResponseDTO result = memberService.login(new MemberLoginRequestDTO("test@example.com", "password"));

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save("1", "refresh-token");
    }

    @Test
    @DisplayName("정지 계정은 비밀번호가 맞아도 로그인 차단")
    void login_suspendedMember() {
        givenAuthenticated(member(MemberStatus.SUSPENDED));

        assertThatThrownBy(() -> memberService.login(new MemberLoginRequestDTO("test@example.com", "password")))
                .isInstanceOf(AccountRestrictedException.class);

        verify(jwtProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(anyString(), anyString());
    }

    @Test
    @DisplayName("탈퇴 계정은 토큰 재발급 차단 및 저장된 리프레시 토큰 삭제")
    void refresh_withdrawnMember() {
        givenRefreshClaims("refresh-token");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(MemberStatus.WITHDRAWN)));

        assertThatThrownBy(() -> memberService.refresh("refresh-token"))
                .isInstanceOf(AccountRestrictedException.class);

        verify(refreshTokenRepository).delete("1");
        verify(refreshTokenRepository, never()).rotate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("대소문자와 공백만 다른 이메일도 중복으로 판단해 가입 차단")
    void signup_duplicateEmail() {
        when(memberRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signupMember(signupRequest("  Test@Example.com ")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 가입으로 DB 유니크 제약에 걸리면 중복 이메일로 처리")
    void signup_uniqueConstraintViolation() {
        when(memberRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(memberRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_members_email"));

        assertThatThrownBy(() -> memberService.signupMember(signupRequest("test@example.com")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("회원가입은 정규화한 이메일과 MEMBER 역할로 저장")
    void signup_success() {
        when(memberRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(memberRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        memberService.signupMember(signupRequest("  Test@Example.com "));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.MEMBER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encodedPassword");
    }

    @Test
    @DisplayName("이메일 중복 확인은 정규화한 이메일로 조회해 가입된 이메일이면 사용 불가")
    void isEmailAvailable_registeredEmail() {
        when(memberRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThat(memberService.isEmailAvailable("  Test@Example.com ")).isFalse();
    }

    @Test
    @DisplayName("가입되지 않은 이메일은 사용 가능")
    void isEmailAvailable_newEmail() {
        when(memberRepository.existsByEmail("new@example.com")).thenReturn(false);

        assertThat(memberService.isEmailAvailable("new@example.com")).isTrue();
    }
}
