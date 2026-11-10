package com.tikit.performance.service;

import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.response.KopisPerformanceItemDTO;
import com.tikit.performance.dto.response.KopisPerformanceSearchResponseDTO;
import com.tikit.performance.entity.Genre;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.kopis.KopisClient;
import com.tikit.performance.kopis.KopisPerformanceSummary;
import com.tikit.performance.repository.GenreRepository;
import com.tikit.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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