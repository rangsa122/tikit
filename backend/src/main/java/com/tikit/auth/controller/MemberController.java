package com.tikit.auth.controller;

import com.tikit.auth.dto.response.JwtTokenResponseDTO;
import com.tikit.auth.dto.response.AccessTokenResponseDTO;
import com.tikit.auth.dto.response.EmailAvailabilityResponseDTO;
import com.tikit.auth.cookie.RefreshTokenCookie;
import com.tikit.auth.dto.request.EmailAvailabilityRequestDTO;
import com.tikit.auth.dto.request.MemberLoginRequestDTO;
import com.tikit.auth.dto.request.MemberRegisterRequestDTO;
import com.tikit.auth.service.MemberService;
import com.tikit.common.dto.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final RefreshTokenCookie refreshTokenCookie;


    @PostMapping("/login")
    public ResponseEntity<ResponseDTO<AccessTokenResponseDTO>> login(@RequestBody MemberLoginRequestDTO dto) {
        JwtTokenResponseDTO tokenDTO = memberService.login(dto);

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(tokenDTO.getRefreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ResponseDTO.success("로그인 성공", new AccessTokenResponseDTO(tokenDTO.getAccessToken())));
    }

    @PostMapping("/signup")
    public ResponseEntity<ResponseDTO<Void>> signup(@Valid @RequestBody MemberRegisterRequestDTO dto) {
        memberService.signupMember(dto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ResponseDTO.success("회원가입 성공."));
    }

    @GetMapping("/email-availability")
    public ResponseEntity<ResponseDTO<EmailAvailabilityResponseDTO>> checkEmailAvailability(
            @Valid @ModelAttribute EmailAvailabilityRequestDTO dto
    ) {
        boolean available = memberService.isEmailAvailable(dto.getEmail());

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ResponseDTO.success(
                        "이메일 중복 확인 성공",
                        new EmailAvailabilityResponseDTO(available)
                ));
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<AccessTokenResponseDTO>> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken
    ) {

        JwtTokenResponseDTO newJwtToken = memberService.refresh(refreshToken);

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(newJwtToken.getRefreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ResponseDTO.success(
                                "토큰 재발급 성공",
                                new AccessTokenResponseDTO(newJwtToken.getAccessToken())
                        )
                );
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseDTO<Void>> logout(@AuthenticationPrincipal String accountId) {
        memberService.logout(accountId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
                .body(ResponseDTO.success("로그아웃 성공"));
    }

}
