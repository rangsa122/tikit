package com.tikit.rag.host;

import com.tikit.util.JWTProvider;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Predicate;

/** 기존 로그인 토큰과 백엔드 전용 RAG 토큰을 확인한다. */
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
            if (!"HS256".equals(signed.getHeader().getAlgorithm())
                    || claims.getExpiration() == null || claims.getIssuedAt() == null) {
                return false;
            }
            var type = claims.get("tokenType", String.class);
            if (JWTProvider.RAG_SERVICE_TOKEN_TYPE.equals(type)) {
                return JWTProvider.RAG_SERVICE_SUBJECT.equals(claims.getSubject());
            }
            return "ACCESS".equals(type) && Long.parseLong(claims.getSubject()) > 0
                    && Set.of("MEMBER", "MANAGER", "ADMIN").contains(claims.get("roleName", String.class));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }
}
