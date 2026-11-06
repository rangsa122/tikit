package com.tikit.auth.cookie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookie {

    public static final String NAME = "refreshToken";
    private static final String PATH = "/api/v1/auth";
    private final boolean secure;

    public RefreshTokenCookie(@Value("${jwt.refresh-cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(String refreshToken) {
        return build(refreshToken, Duration.ofHours(1));
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
