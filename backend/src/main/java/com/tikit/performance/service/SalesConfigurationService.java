package com.tikit.performance.service;

import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.*;
import com.tikit.performance.dto.request.SalesConfigurationSaveRequestDTO;
import com.tikit.performance.dto.response.SalesConfigurationResponseDTO;
import com.tikit.performance.dto.response.SeatTemplateListResponseDTO;
import com.tikit.performance.dto.response.SeatTemplateResponseDTO;
import com.tikit.performance.entity.*;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.PerformanceRepository;
import com.tikit.performance.repository.SaleOpeningRepository;
import com.tikit.performance.repository.SeatTemplateRepository;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import com.tikit.seat.repository.TemplateSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

    private static final String POLICY_VERSION = "v1";
    private static final String CANCELLATION_POLICY ="공연 시작 24시간 전까지 취소가능";

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private final SessionSeatRepository sessionSeatRepository;


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



    @Transactional
    public SalesConfigurationResponseDTO saveSalesConfiguration(Long memberId, Long performanceId,
                                                                SalesConfigurationSaveRequestDTO request) {
        accessChecker.requireManager(memberId);

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND));

        if (performance.getPublicationStatus() != PublicationStatus.DRAFT) {
            throw new PerformanceException(ErrorCode.PERFORMANCE_EDIT_LOCKED);
        }
        if (!performance.getVersion().equals(request.expectedVersion())) {
            throw new PerformanceException(ErrorCode.VERSION_CONFLICT);
        }

        List<PerformanceSession> existing = performanceSessionRepository.findByPerformance(performanceId);
        Set<Long> existingIds = existing.stream().map(PerformanceSession::getId).collect(Collectors.toSet());

        if (!existingIds.isEmpty() && sessionSeatRepository.existsTaken(existingIds)) {
            throw new PerformanceException(ErrorCode.PERFORMANCE_EDIT_LOCKED);
        }

        SalesConfigurationValidator.validate(request, performance.getStartDate(), performance.getEndDate(),
                existingIds, loadTemplateGrades(request));

        //요청에 없는 기존 회차 삭제(삭제순서 : 좌석 -> 가격 -> 회차)
        Set<Long> requestedIds = request.sessions().stream()
                .map(SessionConfigurationDTO::sessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<PerformanceSession> toDelete = existing.stream()
                .filter(s -> !requestedIds.contains(s.getId()))
                .toList();

        if(!toDelete.isEmpty()){
            List<Long> deleteIds = toDelete.stream().map(PerformanceSession::getId).toList();
            sessionSeatRepository.deleteBySessions(deleteIds);
            sessionGradePriceRepository.deleteBySessions(deleteIds);
            performanceSessionRepository.deleteAll(toDelete);
            performanceSessionRepository.flush();
        }

        performance.changeBookingLimit(request.bookingLimitPerMember().shortValue());

        //판매 오픈 찾기, 만들기
        Map<Instant, SaleOpening> openingByTime = new HashMap<>(
                saleOpeningRepository.findByPerformance(performanceId).stream()
                        .collect(Collectors.toMap(SaleOpening::getOpensAt, o ->o)));

        for(SessionConfigurationDTO s : request.sessions()){
            Instant opensAt = s.salesOpenAt().toInstant().truncatedTo(ChronoUnit.MICROS);
            if(!openingByTime.containsKey(opensAt)){
                SaleOpening created = saleOpeningRepository.save(SaleOpening.create(performance, opensAt));
                openingByTime.put(opensAt, created);
            }
        }

        //회차 수정 및 생성(좌석, 가격)
        Map<Long, PerformanceSession> existingById = existing.stream()
                .collect(Collectors.toMap(PerformanceSession::getId, s-> s));
        //남는 기존회차의 가격은 한번에 지움 (아래 반복문에서 새로 넣음)
        List<Long> keptIds = existing.stream()
                .map(PerformanceSession::getId)
                .filter(requestedIds::contains)
                .toList();
        if(!keptIds.isEmpty()){
            sessionGradePriceRepository.deleteBySessions(keptIds);
        }
        Map<Long, List<TemplateSeat>> seatsByTemplate = new HashMap<>();

        for(SessionConfigurationDTO s:request.sessions()){
            Instant startsAt = toInstant(s.startsAt());
            Instant salesCloseAt = toInstant(s.salesCloseAt());
            Instant deadline = toInstant(s.cancellationDeadline());
            Long openingId = openingByTime.get(toInstant(s.salesOpenAt())).getId();

            final PerformanceSession session;
            boolean needSeats;

            //기존회차 수정
            if(s.sessionId() != null) {
                session = existingById.get(s.sessionId());
                needSeats = !session.getSeatTemplateId().equals(s.seatTemplateId());
                if (needSeats) {
                    sessionSeatRepository.deleteBySessions(List.of(session.getId()));
                }
                session.update(openingId, s.seatTemplateId(), startsAt, salesCloseAt, deadline);
                //새회차 생성
            }else{
                session = performanceSessionRepository.save(PerformanceSession.create(
                        performance, openingId, s.seatTemplateId(), startsAt, salesCloseAt, deadline,
                        POLICY_VERSION, CANCELLATION_POLICY));
                needSeats = true;
            }

            // 좌석에 (session_id, grade) 외래키가 걸려 있어 가격을 먼저 저장해야 한다
            sessionGradePriceRepository.saveAll(s.gradePrices().stream()
                    .map(p -> SessionGradePrice.create(session, p.grade(), p.price()))
                    .toList());
            sessionGradePriceRepository.flush();

            if (needSeats) {
                List<TemplateSeat> templateSeats = seatsByTemplate.computeIfAbsent(
                        s.seatTemplateId(), templateSeatRepository::findByTemplateId);
                sessionSeatRepository.saveAll(templateSeats.stream()
                        .map(ts -> SessionSeat.create(session, ts))
                        .toList());
            }
        }

        performanceRepository.flush();
        return buildResponse(performance, performanceSessionRepository.findByPerformance(performanceId));
    }

    private Map<Long, Set<String>> loadTemplateGrades(SalesConfigurationSaveRequestDTO request) {
        Set<Long> templateIds = request.sessions().stream()
                .map(SessionConfigurationDTO::seatTemplateId)
                .collect(Collectors.toSet());

        Set<Long> activeIds = seatTemplateRepository.findAllById(templateIds).stream()
                .filter(SeatTemplate::isActive)
                .map(SeatTemplate::getId)
                .collect(Collectors.toSet());
        if (activeIds.isEmpty()) {
            return Map.of();
        }

        return templateSeatRepository.findByTemplateIdIn(activeIds).stream()
                .collect(Collectors.groupingBy(s -> s.getTemplate().getId(),
                        Collectors.mapping(TemplateSeat::getGrade, Collectors.toSet())));
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

    private static  Instant toInstant(OffsetDateTime time){
        return time.toInstant().truncatedTo(ChronoUnit.MICROS);
    }

}
