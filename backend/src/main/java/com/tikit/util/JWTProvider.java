package com.tikit.util;

import com.tikit.auth.entity.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JWTProvider {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey secretKey;
    public static final String RAG_SERVICE_TOKEN_TYPE = "RAG_SERVICE";
    public static final String RAG_SERVICE_SUBJECT = "tikit-rag-backend";

    private static final long ACCESS_TOKEN_EXPIRATION_MILLIS = 10L * 60 * 1000; // 10분
    private static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 60L * 60 * 1000; // 60분

    @PostConstruct
    private void init() {
        secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(Member member) {
        Date now = new Date() ;
        Date expire = new Date(now.getTime() + (long) ACCESS_TOKEN_EXPIRATION_MILLIS);

        return Jwts.builder()
                .subject(member.getId().toString())
                .claim("roleName", member.getRole().toString())
                .claim("tokenType", "ACCESS")
                .claim("credentialsVersion", member.getCredentialsVersion())
                .issuedAt(now)
                .expiration(expire)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRagServiceToken() {
        Date now = new Date();
        return Jwts.builder()
                .subject(RAG_SERVICE_SUBJECT)
                .claim("tokenType", RAG_SERVICE_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(Member member) {
        Date now = new Date() ;
        Date expire = new Date(now.getTime() + (long) REFRESH_TOKEN_EXPIRATION_MILLIS);

        return Jwts.builder()
                .subject(member.getId().toString())
                .claim("tokenType", "REFRESH")
                .claim("credentialsVersion", member.getCredentialsVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expire)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isExpired(String token) {
        try {
            getClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

}
