package com.tikit.rag.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("rag.remote")
public record RagRemoteProperties(@NotNull URI baseUrl, @Min(1) int connectTimeoutMs,
                                   @Min(1) int readTimeoutMs) {
    @AssertTrue(message = "공용 AI 주소는 인증 정보가 없는 HTTPS 주소여야 합니다. HTTP는 로컬 테스트에서만 허용합니다.")
    public boolean isValidBaseUrl() {
        if (baseUrl == null || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            return false;
        }
        return "https".equalsIgnoreCase(baseUrl.getScheme())
                || ("http".equalsIgnoreCase(baseUrl.getScheme())
                && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(baseUrl.getHost()));
    }
}
