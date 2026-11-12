
package com.tikit.seat.service;

import com.tikit.booking.redis.SeatLockRepository;
import com.tikit.seat.dto.response.SessionSeatResponseDTO;
import com.tikit.performance.entity.SeatStatus;
import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.SessionGradePriceId;
import com.tikit.performance.entity.SessionSeat;
import com.tikit.performance.entity.TemplateSeat;
import com.tikit.seat.repository.PerformanceSessionRepository;
import com.tikit.seat.repository.SessionGradePriceRepository;
import com.tikit.seat.repository.SessionSeatRepository;
import com.tikit.seat.repository.TemplateSeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionSeatServiceTest {

    @Mock
    private PerformanceSessionRepository performanceSessionRepository;

    @Mock
    private SessionSeatRepository sessionSeatRepository;

    @Mock
    private TemplateSeatRepository templateSeatRepository;

    @Mock
    private SessionGradePriceRepository sessionGradePriceRepository;

    @Mock
    private SeatLockRepository seatLockRepository;

    @InjectMocks
    private SessionSeatService sessionSeatService;

    @Test
    void 존재하지_않는_회차는_404를_반환한다() {
        when(performanceSessionRepository.existsById(1L))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> sessionSeatService.getSeats(1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("존재하지 않는 공연 회차입니다.", exception.getReason());

        verifyNoInteractions(
                sessionSeatRepository,
                templateSeatRepository,
                sessionGradePriceRepository
        );
    }

    @Test
    void 좌석이_없는_회차는_빈_목록을_반환한다() {
        when(performanceSessionRepository.existsById(1L))
                .thenReturn(true);

        when(sessionSeatRepository.findBySessionId(1L))
                .thenReturn(List.of());

        List<SessionSeatResponseDTO> result =
                sessionSeatService.getSeats(1L);

        assertTrue(result.isEmpty());

        verifyNoInteractions(
                templateSeatRepository,
                sessionGradePriceRepository
        );
    }

    @Test
    void 좌석_조회_시_등급별_가격도_반환한다() {
        SessionSeat sessionSeat = mock(SessionSeat.class);
        TemplateSeat templateSeat = mock(TemplateSeat.class);
        SessionGradePrice gradePrice = mock(SessionGradePrice.class);

        when(performanceSessionRepository.existsById(1L))
                .thenReturn(true);

        when(sessionSeatRepository.findBySessionId(1L))
                .thenReturn(List.of(sessionSeat));

        when(sessionSeat.getSeatTemplateId()).thenReturn(10L);
        when(sessionSeat.getTemplateSeat()).thenReturn(templateSeat);
        when(sessionSeat.getId()).thenReturn(1000L);
        when(sessionSeat.getGrade()).thenReturn("VIP");
        when(sessionSeat.getStatus()).thenReturn(SeatStatus.AVAILABLE);

        when(templateSeatRepository.findByTemplateId(10L))
                .thenReturn(List.of(templateSeat));

        when(templateSeat.getId()).thenReturn(100L);
        when(templateSeat.getSeatCode()).thenReturn("A1");
        when(templateSeat.getLabel()).thenReturn("A열 1번");
        when(templateSeat.getRowLabel()).thenReturn("A");
        when(templateSeat.getSeatNumber()).thenReturn(1);
        when(templateSeat.getX()).thenReturn(100);
        when(templateSeat.getY()).thenReturn(200);

        when(sessionGradePriceRepository.findBySessionId(1L))
                .thenReturn(List.of(gradePrice));

        when(gradePrice.getId()).thenReturn(new SessionGradePriceId(1L, "VIP"));
        when(gradePrice.getPrice()).thenReturn(150000L);

        List<SessionSeatResponseDTO> result =
                sessionSeatService.getSeats(1L);

        assertEquals(1, result.size());

        SessionSeatResponseDTO seat = result.getFirst();

        assertEquals(1000L, seat.seatId());
        assertEquals("A1", seat.seatCode());
        assertEquals("A열 1번", seat.label());
        assertEquals("A", seat.rowLabel());
        assertEquals(1, seat.seatNumber());
        assertEquals("VIP", seat.grade());
        assertEquals(150000L, seat.price());
        assertEquals(100, seat.x());
        assertEquals(200, seat.y());
        assertEquals("AVAILABLE", seat.status());

        verify(sessionGradePriceRepository).findBySessionId(1L);
    }

    @Test
    void 내가_누른_좌석은_heldByMe로_돌려준다() {
        stubSeat(1000L, SeatStatus.AVAILABLE);
        when(seatLockRepository.findHolders(List.of(1000L))).thenReturn(Map.of(1000L, "my-admission"));

        SessionSeatResponseDTO seat = sessionSeatService.getSeats(1L, "my-admission").getFirst();

        assertEquals("AVAILABLE", seat.status());
        assertTrue(seat.heldByMe());
    }

    @Test
    void 다른_사람이_누른_좌석은_HELD로_돌려준다() {
        stubSeat(1000L, SeatStatus.AVAILABLE);
        when(seatLockRepository.findHolders(List.of(1000L))).thenReturn(Map.of(1000L, "other-admission"));

        SessionSeatResponseDTO seat = sessionSeatService.getSeats(1L, "my-admission").getFirst();

        assertEquals("HELD", seat.status());
        assertFalse(seat.heldByMe());
    }

    @Test
    void 입장_권한_없이_조회해도_잡힌_좌석은_HELD다() {
        stubSeat(1000L, SeatStatus.AVAILABLE);
        when(seatLockRepository.findHolders(List.of(1000L))).thenReturn(Map.of(1000L, "other-admission"));

        assertEquals("HELD", sessionSeatService.getSeats(1L).getFirst().status());
    }

    @Test
    void 판매된_좌석은_락이_남아_있어도_DB_상태를_돌려준다() {
        stubSeat(1000L, SeatStatus.SOLD);
        when(seatLockRepository.findHolders(List.of(1000L))).thenReturn(Map.of(1000L, "my-admission"));

        SessionSeatResponseDTO seat = sessionSeatService.getSeats(1L, "my-admission").getFirst();

        assertEquals("SOLD", seat.status());
        assertFalse(seat.heldByMe());
    }

    @Test
    void Redis_장애면_DB_상태로_응답한다() {
        stubSeat(1000L, SeatStatus.AVAILABLE);
        when(seatLockRepository.findHolders(List.of(1000L)))
                .thenThrow(new RedisConnectionFailureException("down"));

        SessionSeatResponseDTO seat = sessionSeatService.getSeats(1L, "my-admission").getFirst();

        assertEquals("AVAILABLE", seat.status());
        assertFalse(seat.heldByMe());
    }

    private void stubSeat(Long seatId, SeatStatus status) {
        SessionSeat sessionSeat = mock(SessionSeat.class);
        TemplateSeat templateSeat = mock(TemplateSeat.class);

        when(performanceSessionRepository.existsById(1L)).thenReturn(true);
        when(sessionSeatRepository.findBySessionId(1L)).thenReturn(List.of(sessionSeat));
        when(sessionSeat.getSeatTemplateId()).thenReturn(10L);
        when(sessionSeat.getTemplateSeat()).thenReturn(templateSeat);
        when(sessionSeat.getId()).thenReturn(seatId);
        when(sessionSeat.getGrade()).thenReturn("VIP");
        when(sessionSeat.getStatus()).thenReturn(status);
        when(templateSeatRepository.findByTemplateId(10L)).thenReturn(List.of(templateSeat));
        when(templateSeat.getId()).thenReturn(100L);
        when(sessionGradePriceRepository.findBySessionId(1L)).thenReturn(List.of());
    }
}
