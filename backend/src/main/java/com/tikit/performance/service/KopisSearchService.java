package com.tikit.performance.service;

import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.response.KopisPerformanceDetailResponseDTO;
import com.tikit.performance.dto.response.KopisPerformanceItemDTO;
import com.tikit.performance.dto.response.KopisPerformanceSearchResponseDTO;
import com.tikit.performance.entity.Genre;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.kopis.KopisClient;
import com.tikit.performance.kopis.KopisGenreMapper;
import com.tikit.performance.kopis.KopisPerformanceDetail;
import com.tikit.performance.kopis.KopisPerformanceSummary;
import com.tikit.performance.repository.GenreRepository;
import com.tikit.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KopisSearchService {

    private static final int MAX_ROWS = 100;        // KOPIS 제한
    private static final int MAX_RANGE_DAYS = 31;   // KOPIS 제한

    private final PerformanceAccessChecker accessChecker;
    private final GenreRepository genreRepository;
    private final PerformanceRepository performanceRepository;
    private final KopisClient kopisClient;

    public KopisPerformanceSearchResponseDTO search(
            Long memberId,
            LocalDate startDate,
            LocalDate endDate,
            String title,
            String genreCode,
            int page,
            int size
    ) {
        accessChecker.requireViewer(memberId);

        validateRange(startDate, endDate);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_ROWS);

        // 우리 장르 코드를 KOPIS 장르 코드로 바꾼다
        String kopisGenreCode = null;
        if (StringUtils.hasText(genreCode)) {
            kopisGenreCode = genreRepository.findActive(genreCode)
                    .map(Genre::getKopisCode)
                    .orElseThrow(() -> new PerformanceException(ErrorCode.VALIDATION_ERROR));
        }

        // KOPIS의 cpage는 1부터 시작한다
        List<KopisPerformanceSummary> results = kopisClient.searchPerformances(
                startDate, endDate, title, kopisGenreCode, safePage + 1, safeSize);

        Set<String> registered = findRegistered(results);

        List<KopisPerformanceItemDTO> items = results.stream()
                .map(r -> new KopisPerformanceItemDTO(
                        r.kopisId(),
                        r.title(),
                        r.venueName(),
                        r.startDate(),
                        r.endDate(),
                        r.posterUrl(),
                        r.area(),
                        r.genreName(),
                        r.state(),
                        registered.contains(r.kopisId())
                ))
                .toList();

        return new KopisPerformanceSearchResponseDTO(items, safePage, safeSize);
    }


    public KopisPerformanceDetailResponseDTO getDetail(Long memberId, String kopisId) {
        accessChecker.requireViewer(memberId);

        if (!StringUtils.hasText(kopisId)) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        KopisPerformanceDetail detail = kopisClient.getPerformance(kopisId);

        return new KopisPerformanceDetailResponseDTO(
                detail.kopisId(),
                detail.title(),
                resolveGenreCode(detail.genreName()),
                detail.genreName(),
                detail.venueName(),
                detail.organizerName(),
                buildDescription(detail),
                toHttps(detail.posterUrl()),
                detail.startDate(),
                detail.endDate(),
                detail.cast(),
                detail.runtime(),
                detail.age(),
                detail.timeGuidance(),
                detail.priceGuidance(),
                detail.state(),
                performanceRepository.existsByKopisId(detail.kopisId())
        );
    }

    // KOPIS 장르명을 우리 장르 코드로 바꾼다. 매핑에 없거나 비활성 장르면 null
    private String resolveGenreCode(String genreName) {
        String code = KopisGenreMapper.toGenreCode(genreName);
        if (code == null) {
            return null;
        }
        return genreRepository.findActive(code).map(Genre::getCode).orElse(null);
    }

    // KOPIS 포스터는 http로 오지만 https로도 열린다. 우리 페이지가 https라 섞이면 차단된다
    private String toHttps(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return url.startsWith("http://")
                ? "https://" + url.substring("http://".length())
                : url;
    }

    // 줄거리가 비어 있으면 다른 정보로 설명을 만든다. description은 필수값이다
    private String buildDescription(KopisPerformanceDetail detail) {
        if (StringUtils.hasText(detail.story())) {
            return detail.story().trim();
        }

        List<String> lines = new ArrayList<>();
        addLine(lines, "공연장", detail.venueName());
        addLine(lines, "출연", detail.cast());
        addLine(lines, "관람시간", detail.runtime());
        addLine(lines, "관람연령", detail.age());
        addLine(lines, "공연시간 안내", detail.timeGuidance());
        addLine(lines, "티켓가격", detail.priceGuidance());

        return lines.isEmpty() ? detail.title() : String.join("\n", lines);
    }

    private void addLine(List<String> lines, String label, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(label + ": " + value.trim());
        }
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
        if (endDate.isBefore(startDate)) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private Set<String> findRegistered(List<KopisPerformanceSummary> results) {
        List<String> ids = results.stream()
                .map(KopisPerformanceSummary::kopisId)
                .filter(StringUtils::hasText)
                .toList();

        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(performanceRepository.findRegisteredKopisIds(ids));
    }
}