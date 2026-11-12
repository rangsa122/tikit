package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.common.ErrorCode;
import com.tikit.common.idempotency.IdempotencyService;
import com.tikit.performance.dto.response.PerformancePublicationResponseDTO;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformancePublishService {

    private static final String RESOURCE_TYPE = "PERFORMANCE";

    private final PerformanceAccessChecker accessChecker;
    private final PerformanceRepository performanceRepository;
    private final PerformanceSessionRepository performanceSessionRepository;
    private final SessionGradePriceRepository sessionGradePriceRepository;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    /**
     * 공연 게시
     * 멱등 기록은 이 트랜잭션 안에서 성공 처리하여 업무 결과와 함께 커밋된다.
     */
    @Transactional
    public PerformancePublicationResponseDTO publish(
            Long memberId,
            Long performanceId,
            Long expectedVersion,
            Long idempotencyRequestId
    ) {
        Member manager = accessChecker.requireManager(memberId);

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND));
        accessChecker.requireOwner(manager, performance);

        if (performance.getPublicationStatus() != PublicationStatus.DRAFT) {
            throw new PerformanceException(ErrorCode.PERFORMANCE_ALREADY_PUBLISHED);
        }

        if (!performance.getVersion().equals(expectedVersion)) {
            throw new PerformanceException(ErrorCode.VERSION_CONFLICT);
        }

        requirePublishable(performanceId);

        performance.publish(manager, Instant.now(clock).truncatedTo(ChronoUnit.MICROS));

        idempotencyService.succeed(idempotencyRequestId, RESOURCE_TYPE, performanceId);

        performanceRepository.flush();

        return PerformancePublicationResponseDTO.from(performance);
    }

    /**
     * 같은 멱등키로 다시 들어온 요청에 현재 상태를 돌려준다.
     */
    @Transactional(readOnly = true)
    public PerformancePublicationResponseDTO getPublication(Long memberId, Long performanceId) {
        Member member = accessChecker.requireViewer(memberId);

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND));
        accessChecker.requireOwner(member, performance);

        return PerformancePublicationResponseDTO.from(performance);
    }

    /**
     * 게시 조건: 회차가 하나 이상 있고, 모든 회차에 등급별 가격이 있어야 한다.
     */
    private void requirePublishable(Long performanceId) {
        List<PerformanceSession> sessions =
                performanceSessionRepository.findByPerformance(performanceId);

        if (sessions.isEmpty()) {
            throw new PerformanceException(ErrorCode.PUBLISH_REQUIREMENTS_MISSING);
        }

        List<Long> sessionIds = sessions.stream()
                .map(PerformanceSession::getId)
                .toList();

        Set<Long> pricedSessionIds = sessionGradePriceRepository.findBySessions(sessionIds).stream()
                .map(price -> price.getId().getSessionId())
                .collect(Collectors.toSet());

        if (!pricedSessionIds.containsAll(sessionIds)) {
            throw new PerformanceException(ErrorCode.PUBLISH_REQUIREMENTS_MISSING);
        }
    }
}