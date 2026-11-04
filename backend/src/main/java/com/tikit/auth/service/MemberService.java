package com.tikit.auth.service;

import com.tikit.auth.dto.response.JwtTokenResponseDTO;
import com.tikit.auth.dto.request.MemberLoginRequestDTO;
import com.tikit.auth.dto.request.MemberRegisterRequestDTO;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.exception.AccountRestrictedException;
import com.tikit.auth.exception.DuplicateEmailException;
import com.tikit.auth.exception.LoginException;
import com.tikit.auth.exception.RefreshTokenException;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.config.security.CustomUserDetails;
import com.tikit.util.JWTProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JWTProvider jwtProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public void signupMember(MemberRegisterRequestDTO dto) {

        // 저장 형식과 같게 정규화한 이메일로 중복 확인
        String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }

        String encodedPw = passwordEncoder.encode(dto.getPassword());

        Member member = Member.create(
                dto.getEmail(),
                encodedPw,
                dto.getName(),
                Role.MEMBER
        );

        Member saved;
        try {
            // 동시 가입 요청은 DB 유니크 제약으로 한 번 더 차단
            saved = memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }

        log.info("registered user: {}", saved.getId());

    }

    // 탈퇴 회원의 이메일도 행이 남아 있으므로 사용 중으로 판단
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(email.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public JwtTokenResponseDTO login(MemberLoginRequestDTO dto) {

        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            dto.getEmail(),
                            dto.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new LoginException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Member member = userDetails.getMember();

        // 비밀번호 확인 후 계정 상태 검사. 정지 또는 탈퇴 계정은 토큰 발급 차단
        validateActive(member);

        String accessToken = jwtProvider.generateAccessToken(member);
        String refreshToken = jwtProvider.generateRefreshToken(member);

        refreshTokenRepository.save(member.getId().toString(), refreshToken);

        log.info("login success: {}", member.getId());

        return new JwtTokenResponseDTO(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public JwtTokenResponseDTO refresh(String refreshToken) {

        Claims claims;
        // refreshToken 에서 정보 추출 및 토큰 만료 확인
        try {
            claims = jwtProvider.getClaims(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new RefreshTokenException("Refresh Token이 만료되었습니다.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new RefreshTokenException("유효하지 않은 Refresh Token입니다.");
        }

        if (!"REFRESH".equals(claims.get("tokenType", String.class))) {
            throw new RefreshTokenException("Refresh Token이 아닙니다.");
        }

        String accountId = claims.getSubject();

        Member member = memberRepository.findById(Long.valueOf(accountId))
                .orElseThrow(() -> new RefreshTokenException("계정이 존재하지 않습니다."));

        // 로그인 이후 정지 또는 탈퇴된 계정은 세션 삭제 후 재발급 차단
        if (member.getStatus() != MemberStatus.ACTIVE) {
            refreshTokenRepository.delete(accountId);
            validateActive(member);
        }

        String newAccessToken = jwtProvider.generateAccessToken(member);
        String newRefreshToken = jwtProvider.generateRefreshToken(member);

        long result = refreshTokenRepository.rotate(
                accountId,
                refreshToken,
                newRefreshToken
        );

        if (result == 0) {
            throw new RefreshTokenException("저장된 세션이 존재하지 않습니다.");
        }

        if (result == -1) {
            throw new RefreshTokenException("토큰 재사용 감지. 세션을 종료합니다.");
        }


        return new JwtTokenResponseDTO(newAccessToken, newRefreshToken);
    }

    public void logout(String accountId) {
        refreshTokenRepository.delete(accountId);

    }

    private void validateActive(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new AccountRestrictedException("이용이 제한된 계정입니다. status=" + member.getStatus());
        }
    }
}
