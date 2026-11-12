package com.tikit.performance.kopis;

import java.util.Map;

// KOPIS 장르명(genrenm) -> 우리 장르 코드
// KOPIS는 상세 조회에서 장르를 코드가 아니라 이름으로 준다.
// 매핑에 없으면 null을 돌려주고, 운영자가 등록 폼에서 직접 고른다.
public final class KopisGenreMapper {

    private static final Map<String, String> BY_KOPIS_NAME = Map.ofEntries(
            Map.entry("연극", "theater"),
            Map.entry("무용", "dance"),
            Map.entry("무용(서양/한국무용)", "dance"),
            Map.entry("대중무용", "popular_dance"),
            Map.entry("서양음악(클래식)", "classical"),
            Map.entry("클래식", "classical"),
            Map.entry("한국음악(국악)", "korean_traditional"),
            Map.entry("국악", "korean_traditional"),
            Map.entry("대중음악", "concert"),
            Map.entry("복합", "complex"),
            Map.entry("서커스/마술", "circus_magic"),
            Map.entry("뮤지컬", "musical")
    );

    private KopisGenreMapper() {
    }

    public static String toGenreCode(String kopisGenreName) {
        if (kopisGenreName == null) {
            return null;
        }
        return BY_KOPIS_NAME.get(kopisGenreName.trim());
    }
}