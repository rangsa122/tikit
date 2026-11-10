package com.tikit.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// 요청 비교용 해시 (명세: 대상 리소스 + 정규화한 본문으로 같은 요청인지 판단)
public final class IdempotencyHasher {
    private IdempotencyHasher() {

    }

    // target 예: "PAYMENT_ATTEMPT:10", normalizedBody 예: 요청 DTO를 JSON으로 바꾼 문자열 (본문 없으면 null)
    public static String hash(String target, String normalizedBody) {
        String source = target + "|" + (normalizedBody == null ? "" : normalizedBody);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
