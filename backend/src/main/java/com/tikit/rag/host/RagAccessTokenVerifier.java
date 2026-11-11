package com.tikit.rag.host;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Predicate;

/** 기존 프로젝트 로그인 토큰만 허용한다. 새 공유 키를 팀원에게 배포하지 않는다. */
public final class RagAccessTokenVerifier implements Predicate<String> {
    private final javax.crypto.SecretKey key;

    public RagAccessTokenVerifier(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean test(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        try {
            var signed = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authorization.substring(7));
            var claims = signed.getPayload();
            return "HS256".equals(signed.getHeader().getAlgorithm())
                    && "ACCESS".equals(claims.get("tokenType", String.class))
                    && claims.getExpiration() != null && claims.getIssuedAt() != null
                    && Long.parseLong(claims.getSubject()) > 0
                    && Set.of("MEMBER", "MANAGER", "ADMIN").contains(claims.get("roleName", String.class));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }
}
