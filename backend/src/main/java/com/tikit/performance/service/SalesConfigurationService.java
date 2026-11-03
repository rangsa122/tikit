package com.tikit.performance.service;

import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.*;
import com.tikit.performance.dto.response.SalesConfigurationResponseDTO;
import com.tikit.performance.entity.*;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.performance.repository.SaleOpeningRepository;
import com.tikit.performance.repository.SeatTemplateRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import com.tikit.seat.repository.TemplateSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesConfigurationService {

    private final PerformanceAccessChecker accessChecker;
    private final SeatTemplateRepository seatTemplateRepository;
    private final TemplateSeatRepository templateSeatRepository;
    private final PerformanceRepository performanceRepository;
    private final PerformanceSessionRepository performanceSessionRepository;
    private final SaleOpeningRepository saleOpeningRepository;
    private final SessionGradePriceRepository sessionGradePriceRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);


    @Transactional(readOnly = true)
    public SeatTemplateListResponseDTO listSeatTemplate(Long memberId){
        accessChecker.requireViewer(memberId);

        List<SeatTemplate> templates = seatTemplateRepository.findActive();
        if(templates.isEmpty()){
            return new SeatTemplateListResponseDTO(List.of());
        }

        List<Long> ids = templates.stream().map(SeatTemplate::getId).toList();
        Map<Long, List<TemplateSeat>> seatsByTemplate = templateSeatRepository.findByTemplateIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(s ->s.getTemplate().getId()));

        List<SeatTemplateResponseDTO> items = templates.stream()
                .map(t->new SeatTemplateResponseDTO(t.getId(), t.getName(),
                        seatsByTemplate.getOrDefault(t.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(TemplateSeat::getRowLabel)
                                        .thenComparingInt(TemplateSeat::getSeatNumber))
                                .map(SeatTemplateSeatDTO::from)
                                .toList()))
                .toList();

        return  new SeatTemplateListResponseDTO(items);
    }

    @Transactional(readOnly = true)
    public SalesConfigurationResponseDTO getSalesConfiguration(Long memberId, Long performanceId){
        accessChecker.requireViewer(memberId);

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND));

        List<PerformanceSession> sessions = performanceSessionRepository.findByPerformance(performanceId);
        return  buildResponse(performance,sessions);

    }

    private SalesConfigurationResponseDTO buildResponse(Performance performance, List<PerformanceSession> sessions){
        if(sessions.isEmpty()){
            return new SalesConfigurationResponseDTO(performance.getId(), performance.getVersion(),
                    (int)performance.getBookingLimitPerMember(), List.of());
        }

        List<Long> sessionIds = sessions.stream().map(PerformanceSession::getId).toList();
        List<Long> openingIds = sessions.stream().map(PerformanceSession::getOpeningId).distinct().toList();

        Map<Long, SaleOpening> openingById = saleOpeningRepository.findAllById(openingIds).stream()
                .collect(Collectors.toMap(SaleOpening::getId, Function.identity()));

        Map<Long, List<SessionGradePrice>> pricesBySession = sessionGradePriceRepository.findBySessions(sessionIds).stream()
                .collect(Collectors.groupingBy(p -> p.getId().getSessionId()));

        List<SessionConfigurationDTO> sessionDtos = sessions.stream()
                .map(s->new SessionConfigurationDTO(
                        s.getId(),
                        s.getStartsAt().atOffset(KST),
                        openingById.get(s.getOpeningId()).getOpensAt().atOffset(KST),
                        s.getSalesCloseAt().atOffset(KST),
                        s.getCancellationDeadline().atOffset(KST),
                        s.getSeatTemplateId(),
                        pricesBySession.getOrDefault(s.getId(),List.of()).stream()
                                .sorted(Comparator.comparing(p -> p.getId().getGrade()))
                                .map(p-> new GradePriceDTO(p.getId().getGrade(), p.getPrice()))
                                .toList()))
                .toList();

        return new SalesConfigurationResponseDTO(performance.getId(), performance.getVersion(),
                (int)performance.getBookingLimitPerMember(), sessionDtos);
    }

}
