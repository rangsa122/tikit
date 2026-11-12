package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.PerformanceSessionListDTO;
import com.tikit.performance.dto.PublicPerformanceDetailDTO;
import com.tikit.performance.dto.PublicPerformancePageDTO;
import com.tikit.performance.dto.PublicPerformanceSummaryDTO;
import com.tikit.performance.dto.request.PerformanceCreateRequestDTO;
import com.tikit.performance.dto.request.PerformanceUpdateRequestDTO;
import com.tikit.performance.dto.request.SalesStatusUpdateRequestDTO;
import com.tikit.performance.dto.request.VisibilityUpdateRequestDTO;
import com.tikit.performance.dto.response.PerformancePublicationResponseDTO;
import com.tikit.performance.dto.response.PerformanceResponseDTO;
import com.tikit.performance.dto.response.PerformanceSessionResponseDTO;
import com.tikit.performance.entity.*;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.GenreRepository;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.performance.repository.SaleOpeningRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    private final PerformanceRepository performanceRepository;
    private final GenreRepository genreRepository;
    private final PerformanceAccessChecker accessChecker;
    private final PerformanceSessionRepository performanceSessionRepository;
    private final SaleOpeningRepository saleOpeningRepository;
    private final SessionGradePriceRepository sessionGradePriceRepository;
    private final SessionSeatRepository sessionSeatRepository;

    /**
     * 관리자용 공연 등록
     */
    @Transactional
    public PerformanceResponseDTO create(
            Long memberId,
            PerformanceCreateRequestDTO request
    ) {
        Member manager = accessChecker.requireManager(memberId);

        validateSource(request);
        validateDates(request.getStartDate(), request.getEndDate());

        Genre genre = findActiveGenre(request.getGenreCode());

        Performance performance;

        if (request.getSource() == PerformanceSource.MANUAL) {
            performance = Performance.createManual(
                    manager,
                    genre,
                    request.getTitle(),
                    request.getVenueName(),
                    request.getOrganizerName(),
                    request.getDescription(),
                    request.getPosterUrl(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getBookingLimitPerMember()
            );
        } else {
            if (performanceRepository.existsByKopisId(request.getKopisId())) {
                throw new PerformanceException(
                        ErrorCode.KOPIS_ALREADY_REGISTERED
                );
            }

            performance = Performance.createFromKopis(
                    manager,
                    genre,
                    request.getKopisId(),
                    request.getTitle(),
                    request.getVenueName(),
                    request.getOrganizerName(),
                    request.getDescription(),
                    request.getPosterUrl(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getBookingLimitPerMember()
            );
        }

        performanceRepository.save(performance);

        return PerformanceResponseDTO.from(performance);
    }

    /**
     * 관리자용 공연 목록 조회
     */
    public Page<PerformanceResponseDTO> list(
            Long memberId,
            PublicationStatus status,
            int page,
            int size
    ) {
        accessChecker.requireViewer(memberId);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        return performanceRepository
                .findAllByStatus(
                        status,
                        PageRequest.of(safePage, safeSize)
                )
                .map(PerformanceResponseDTO::from);
    }

    /**
     * 관리자용 공연 상세 조회
     */
    public PerformanceResponseDTO get(
            Long memberId,
            Long performanceId
    ) {
        accessChecker.requireViewer(memberId);

        return PerformanceResponseDTO.from(
                findPerformance(performanceId)
        );
    }

    /**
     * 일반 사용자용 공개 공연 목록 검색
     */
    public PublicPerformancePageDTO getPublicPerformances(
            int page,
            int size,
            String keyword,
            String genreCode,
            LocalDate fromDate,
            LocalDate toDate,
            String sort
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || (fromDate != null && toDate != null && fromDate.isAfter(toDate))
                || (sort != null && !sort.equals("startDate"))) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        String normalizedGenreCode = StringUtils.hasText(genreCode) ? genreCode.trim() : null;
        LocalDate searchFromDate = fromDate != null ? fromDate : LocalDate.of(1, 1, 1);
        LocalDate searchToDate = toDate != null ? toDate : LocalDate.of(9999, 12, 31);

        Page<Performance> performances = performanceRepository.searchPublicPerformances(
                PublicationStatus.PUBLISHED,
                Visibility.PUBLIC,
                normalizedKeyword,
                normalizedGenreCode,
                searchFromDate,
                searchToDate,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.asc("startDate"),
                        Sort.Order.asc("id")
                ))
        );

        List<Long> performanceIds = performances.getContent().stream()
                .map(Performance::getId)
                .toList();

        if (performanceIds.isEmpty()) {
            return new PublicPerformancePageDTO(
                    List.of(), page, size,
                    performances.getTotalElements(),
                    performances.getTotalPages()
            );
        }

        List<PerformanceSession> sessions =
                performanceSessionRepository.findByPerformanceIds(performanceIds);

        List<Long> sessionIds = sessions.stream()
                .map(PerformanceSession::getId)
                .toList();

        List<SessionGradePrice> prices = sessionIds.isEmpty()
                ? List.of()
                : sessionGradePriceRepository.findBySessions(sessionIds);

        Map<Long, Long> sessionToPerformance = sessions.stream()
                .collect(Collectors.toMap(
                        PerformanceSession::getId,
                        session -> session.getPerformance().getId()
                ));

        Map<Long, Long> minPrices = prices.stream()
                .collect(Collectors.groupingBy(
                        price -> sessionToPerformance.get(price.getId().getSessionId()),
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparingLong(SessionGradePrice::getPrice)),
                                optional -> optional.orElseThrow().getPrice()
                        )
                ));

        Instant now = Instant.now();

        Map<Long, OffsetDateTime> nextOpenings =
                saleOpeningRepository.findByPerformanceIds(performanceIds).stream()
                        .filter(opening -> opening.getOpensAt().isAfter(now))
                        .collect(Collectors.groupingBy(
                                opening -> opening.getPerformance().getId(),
                                Collectors.collectingAndThen(
                                        Collectors.minBy(Comparator.comparing(SaleOpening::getOpensAt)),
                                        optional -> toOffsetDateTime(optional.orElseThrow().getOpensAt())
                                )
                        ));

        List<PublicPerformanceSummaryDTO> items = performances.getContent().stream()
                .map(performance -> {
                    Long minPrice = minPrices.get(performance.getId());

                    if (minPrice == null) {
                        throw new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND);
                    }

                    return new PublicPerformanceSummaryDTO(
                            performance.getId().toString(),
                            performance.getTitle(),
                            performance.getGenre().getCode(),
                            performance.getVenueName(),
                            performance.getPosterUrl(),
                            performance.getStartDate(),
                            performance.getEndDate(),
                            minPrice,
                            performance.getSalesStatus().name(),
                            nextOpenings.get(performance.getId())
                    );
                })
                .toList();

        return new PublicPerformancePageDTO(
                items,
                performances.getNumber(),
                performances.getSize(),
                performances.getTotalElements(),
                performances.getTotalPages()
        );
    }
    /**
     * 일반 사용자용 공개 공연 상세 조회
     */
    public PublicPerformanceDetailDTO getPublicDetail(Long performanceId) {
        Performance performance = findPublicPerformance(performanceId);

        List<PerformanceSession> sessions =
                performanceSessionRepository
                        .findByPerformance_IdOrderByStartsAtAsc(performanceId);

        List<SaleOpening> openings =
                saleOpeningRepository
                        .findByPerformance_IdOrderByOpensAtAsc(performanceId);

        List<Long> sessionIds = sessions.stream()
                .map(PerformanceSession::getId)
                .toList();

        List<SessionGradePrice> prices = sessionIds.isEmpty()
                ? List.of()
                : sessionGradePriceRepository.findBySessionIdIn(sessionIds);

        Long minPrice = prices.stream()
                .map(SessionGradePrice::getPrice)
                .min(Long::compareTo)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        Instant now = Instant.now();

        OffsetDateTime nextSalesOpenAt = openings.stream()
                .map(SaleOpening::getOpensAt)
                .filter(time -> time.isAfter(now))
                .min(Instant::compareTo)
                .map(this::toOffsetDateTime)
                .orElse(null);

        List<PublicPerformanceDetailDTO.SaleOpeningItem> saleOpeningItems =
                openings.stream()
                        .map(opening ->
                                new PublicPerformanceDetailDTO.SaleOpeningItem(
                                        opening.getId().toString(),
                                        toOffsetDateTime(opening.getOpensAt()),
                                        opening.getNotificationProcessedAt() != null
                                )
                        )
                        .toList();

        String cancellationPolicy = sessions.stream()
                .map(PerformanceSession::getCancellationPolicy)
                .filter(Objects::nonNull)
                .distinct()
                .reduce((first, second) -> {
                    throw new IllegalStateException(
                            "회차별 취소 정책이 서로 다릅니다."
                    );
                })
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        return new PublicPerformanceDetailDTO(
                performance.getId().toString(),
                performance.getTitle(),
                performance.getGenre().getCode(),
                performance.getVenueName(),
                performance.getPosterUrl(),
                performance.getStartDate(),
                performance.getEndDate(),
                minPrice,
                performance.getSalesStatus().name(),
                nextSalesOpenAt,
                performance.getDescription(),
                performance.getKopisId(),
                saleOpeningItems,
                (int) performance.getBookingLimitPerMember(),
                cancellationPolicy
        );
    }

    /**
     * 일반 사용자용 공개 공연 회차 목록 조회
     */
    public PerformanceSessionListDTO getPublicSessions(Long performanceId) {
        Performance performance = findPublicPerformance(performanceId);

        List<PerformanceSession> sessions =
                performanceSessionRepository
                        .findByPerformance_IdOrderByStartsAtAsc(performanceId);

        if (sessions.isEmpty()) {
            return new PerformanceSessionListDTO(List.of());
        }

        List<SaleOpening> openings =
                saleOpeningRepository
                        .findByPerformance_IdOrderByOpensAtAsc(performanceId);

        Map<Long, SaleOpening> openingMap = openings.stream()
                .collect(Collectors.toMap(
                        SaleOpening::getId,
                        Function.identity()
                ));

        List<Long> sessionIds = sessions.stream()
                .map(PerformanceSession::getId)
                .toList();

        Map<Long, List<SessionGradePrice>> priceMap =
                sessionGradePriceRepository
                        .findBySessionIdIn(sessionIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                price -> price.getId().getSessionId()
                        ));

        Instant now = Instant.now();
        OffsetDateTime serverTime = toOffsetDateTime(now);

        List<PerformanceSessionResponseDTO> items = sessions.stream()
                .map(session -> {
                    SaleOpening opening = openingMap.get(session.getOpeningId());

                    if (opening == null) {
                        throw new PerformanceException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        );
                    }

                    List<PerformanceSessionResponseDTO.GradePrice> gradePrices =
                            priceMap.getOrDefault(
                                            session.getId(),
                                            List.of()
                                    )
                                    .stream()
                                    .sorted(Comparator.comparing(
                                            price -> price.getId().getGrade()
                                    ))
                                    .map(price ->
                                            new PerformanceSessionResponseDTO.GradePrice(
                                                    price.getId().getGrade(),
                                                    price.getPrice()
                                            )
                                    )
                                    .toList();

                    String bookingAvailability =
                            calculateBookingAvailability(
                                    performance,
                                    session,
                                    opening,
                                    now
                            );

                    return new PerformanceSessionResponseDTO(
                            session.getId().toString(),
                            performance.getId().toString(),
                            toOffsetDateTime(session.getStartsAt()),
                            toOffsetDateTime(opening.getOpensAt()),
                            toOffsetDateTime(session.getSalesCloseAt()),
                            toOffsetDateTime(session.getCancellationDeadline()),
                            bookingAvailability,
                            gradePrices,
                            serverTime
                    );
                })
                .toList();

        return new PerformanceSessionListDTO(items);
    }

    /**
     * 예매 가능 상태 계산
     */
    private String calculateBookingAvailability(
            Performance performance,
            PerformanceSession session,
            SaleOpening opening,
            Instant now
    ) {
        if (performance.getSalesStatus() == SalesStatus.STOPPED) {
            return "STOPPED";
        }

        if (now.isBefore(opening.getOpensAt())) {
            return "NOT_OPEN";
        }

        if (!now.isBefore(session.getSalesCloseAt())) {
            return "CLOSED";
        }

        long available = sessionSeatRepository
                .countBySessionIdAndStatus(
                        session.getId(),
                        SeatStatus.AVAILABLE
                );

        if (available > 0) {
            return "OPEN";
        }

        long held = sessionSeatRepository
                .countBySessionIdAndStatus(
                        session.getId(),
                        SeatStatus.HELD
                );

        if (held > 0) {
            return "OPEN";
        }

        long sold = sessionSeatRepository
                .countBySessionIdAndStatus(
                        session.getId(),
                        SeatStatus.SOLD
                );

        if (sold > 0) {
            return "SOLD_OUT";
        }

        throw new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 관리자용 공연 수정
     */
    @Transactional
    public PerformanceResponseDTO update(
            Long memberId,
            Long performanceId,
            PerformanceUpdateRequestDTO request
    ) {
        accessChecker.requireManager(memberId);

        Performance performance = findPerformance(performanceId);

        if (performance.getPublicationStatus() != PublicationStatus.DRAFT) {
            throw new PerformanceException(
                    ErrorCode.PERFORMANCE_EDIT_LOCKED
            );
        }

        if (!performance.getVersion().equals(request.getExpectedVersion())) {
            throw new PerformanceException(
                    ErrorCode.VERSION_CONFLICT
            );
        }

        LocalDate newStart = request.getStartDate() != null
                ? request.getStartDate()
                : performance.getStartDate();

        LocalDate newEnd = request.getEndDate() != null
                ? request.getEndDate()
                : performance.getEndDate();

        validateDates(newStart, newEnd);

        Genre genre = request.getGenreCode() == null
                ? null
                : findActiveGenre(request.getGenreCode());

        performance.update(
                genre,
                request.getTitle(),
                request.getVenueName(),
                request.getOrganizerName(),
                request.getDescription(),
                request.getPosterUrl(),
                request.getStartDate(),
                request.getEndDate(),
                request.getBookingLimitPerMember()
        );

        performanceRepository.flush();

        return PerformanceResponseDTO.from(performance);
    }

    /**
     * 공개 공연 조회 공통 메서드
     */
    private Performance findPublicPerformance(Long performanceId) {
        return performanceRepository
                .findPublicDetail(
                        performanceId,
                        PublicationStatus.PUBLISHED,
                        Visibility.PUBLIC
                )
                .orElseThrow(() ->
                        new PerformanceException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );
    }

    /**
     * 공연 상세 조회
     */
    private Performance findPerformance(Long id) {
        return performanceRepository.findDetail(id)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND)
                );
    }

    /**
     * 활성화된 장르 조회
     */
    private Genre findActiveGenre(String code) {
        return genreRepository.findActive(code)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.VALIDATION_ERROR)
                );
    }

    /**
     * 공연 날짜 검증
     */
    private void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * 공연 등록 출처 검증
     */
    private void validateSource(PerformanceCreateRequestDTO request) {
        boolean hasKopisId = StringUtils.hasText(request.getKopisId());

        if (request.getSource() == PerformanceSource.MANUAL
                && hasKopisId) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        if (request.getSource() == PerformanceSource.KOPIS
                && !hasKopisId) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * 한국 시간대 변환
     */
    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(RESPONSE_ZONE).toOffsetDateTime();
    }

    /**
     * 신규 판매 중단 또는 재개
     */
    @Transactional
    public PerformanceResponseDTO changeSalesStatus(
            Long memberId,
            Long performanceId,
            SalesStatusUpdateRequestDTO request
    ) {
        accessChecker.requireManager(memberId);

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        if (request.getSalesStatus() == SalesStatus.ENABLED) {
            // 재개: 게시됐고 공개 상태일 때만 가능
            boolean resumable = performance.getPublicationStatus() == PublicationStatus.PUBLISHED
                    && performance.getVisibility() == Visibility.PUBLIC;
            if (!resumable) {
                throw new PerformanceException(ErrorCode.SALE_RESUME_NOT_ALLOWED);
            }
        } else if (performance.getPublicationStatus() != PublicationStatus.PUBLISHED) {

            throw new PerformanceException(ErrorCode.PERFORMANCE_NOT_PUBLISHED);
        }

        if (!performance.getVersion().equals(request.getExpectedVersion())) {
            throw new PerformanceException(ErrorCode.VERSION_CONFLICT);
        }

        performance.changeSalesStatus(request.getSalesStatus());

        performanceRepository.flush();

        return PerformanceResponseDTO.from(performance);
    }

    /**
     * 공연 게시 중단 또는 복구 (K-08)
     */
    @Transactional
    public PerformancePublicationResponseDTO changeVisibility(
            Long memberId,
            Long performanceId,
            VisibilityUpdateRequestDTO request
    ) {
        accessChecker.requireManager(memberId);

        // 이 API로는 PUBLIC, HIDDEN만 지정할 수 있다 (PRIVATE은 게시 전 상태)
        if (request.getVisibility() != Visibility.PUBLIC
                && request.getVisibility() != Visibility.HIDDEN) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() ->
                        new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        if (performance.getPublicationStatus() != PublicationStatus.PUBLISHED) {
            throw new PerformanceException(ErrorCode.PERFORMANCE_NOT_PUBLISHED);
        }

        if (!performance.getVersion().equals(request.getExpectedVersion())) {
            throw new PerformanceException(ErrorCode.VERSION_CONFLICT);
        }

        // TODO: request.getReason()을 audit_logs에 남길지 팀 확인 필요 (현재는 저장하지 않음)
        performance.changeVisibility(request.getVisibility());

        performanceRepository.flush();

        return PerformancePublicationResponseDTO.from(performance);
    }
}
