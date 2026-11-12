
package com.tikit.seat.service;

import com.tikit.booking.redis.SeatLockRepository;
import com.tikit.performance.entity.SeatStatus;
import com.tikit.seat.dto.response.SessionSeatResponseDTO;
import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.SessionSeat;
import com.tikit.performance.entity.TemplateSeat;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import com.tikit.seat.repository.TemplateSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSeatService {

    private final PerformanceSessionRepository performanceSessionRepository;
    private final SessionSeatRepository sessionSeatRepository;
    private final TemplateSeatRepository templateSeatRepository;
    private final SessionGradePriceRepository sessionGradePriceRepository;
    private final SeatLockRepository seatLockRepository;

    public List<SessionSeatResponseDTO> getSeats(Long sessionId) {
        return getSeats(sessionId, null);
    }

    // admissionId를 주면 내가 누른 좌석은 heldByMe로, 다른 사람이 누른 좌석은 HELD로 알려 줌
    @Transactional(readOnly = true)
    public List<SessionSeatResponseDTO> getSeats(Long sessionId, String admissionId) {

        if (!performanceSessionRepository.existsById(sessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 공연 회차입니다."
            );
        }

        List<SessionSeat> sessionSeats =
                sessionSeatRepository.findBySessionId(sessionId);

        if (sessionSeats.isEmpty()) {
            return List.of();
        }

        Long templateId = sessionSeats.getFirst().getSeatTemplateId();

        Map<Long, TemplateSeat> templateSeatMap =
                templateSeatRepository.findByTemplateId(templateId)
                        .stream()
                        .collect(Collectors.toMap(
                                TemplateSeat::getId,
                                Function.identity()
                        ));

        Map<String, Long> priceMap =
                sessionGradePriceRepository.findBySessionId(sessionId)
                        .stream()
                        .collect(Collectors.toMap(
                                price -> price.getId().getGrade(),
                                SessionGradePrice::getPrice
                        ));

        Map<Long, String> holderBySeatId = findHolders(sessionSeats);

        return sessionSeats.stream()
                .map(seat -> {
                    TemplateSeat templateSeat =
                            templateSeatMap.get(seat.getTemplateSeat().getId());

                    Long price = priceMap.get(seat.getGrade());
                    String holder = holderBySeatId.get(seat.getId());
                    boolean available = seat.getStatus() == SeatStatus.AVAILABLE;
                    boolean heldByMe = available && holder != null && holder.equals(admissionId);
                    boolean heldByOther = available && holder != null && !heldByMe;

                    return new SessionSeatResponseDTO(
                            seat.getId(),
                            templateSeat.getSeatCode(),
                            templateSeat.getLabel(),
                            templateSeat.getRowLabel(),
                            templateSeat.getSeatNumber(),
                            seat.getGrade(),
                            price,
                            templateSeat.getX(),
                            templateSeat.getY(),
                            heldByOther ? SeatStatus.HELD.name() : seat.getStatus().name(),
                            heldByMe
                    );
                })
                .toList();
    }

    // Redis 장애면 DB 상태만 보여 줌. 실제 선점은 예매 생성 때 다시 검사함
    private Map<Long, String> findHolders(List<SessionSeat> sessionSeats) {
        try {
            return seatLockRepository.findHolders(sessionSeats.stream().map(SessionSeat::getId).toList());
        } catch (DataAccessException e) {
            log.warn("좌석 선점 상태 조회 실패, DB 상태로 응답", e);
            return Map.of();
        }
    }
}
