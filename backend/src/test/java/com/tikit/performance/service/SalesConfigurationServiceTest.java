package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.SeatTemplateListResponseDTO;
import com.tikit.performance.entity.SeatTemplate;
import com.tikit.performance.entity.TemplateSeat;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.SeatTemplateRepository;
import com.tikit.seat.repository.TemplateSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SalesConfigurationServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    SeatTemplateRepository seatTemplateRepository;
    @Mock
    TemplateSeatRepository templateSeatRepository;

    SalesConfigurationService service;

    @BeforeEach
    void setUp() {
        PerformanceAccessChecker checker = new PerformanceAccessChecker(memberRepository);
        service = new SalesConfigurationService(checker, seatTemplateRepository, templateSeatRepository);
    }

    private Member member(Role role) {
        Member m = Member.create("test@test.com", "1234", "김철수", role);
        ReflectionTestUtils.setField(m, "id", 1L);
        ReflectionTestUtils.setField(m, "status", MemberStatus.ACTIVE);
        return m;
    }

    private SeatTemplate template(Long id, String name) {
        SeatTemplate t = SeatTemplate.create(name, 1, true);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private TemplateSeat seat(SeatTemplate t, String code, String row, int number, String grade) {
        TemplateSeat s = BeanUtils.instantiateClass(TemplateSeat.class);
        ReflectionTestUtils.setField(s, "template", t);
        ReflectionTestUtils.setField(s, "seatCode", code);
        ReflectionTestUtils.setField(s, "label", code);
        ReflectionTestUtils.setField(s, "rowLabel", row);
        ReflectionTestUtils.setField(s, "seatNumber", number);
        ReflectionTestUtils.setField(s, "grade", grade);
        ReflectionTestUtils.setField(s, "x", 0);
        ReflectionTestUtils.setField(s, "y", 0);
        return s;
    }

    @Test
    @DisplayName("템플릿별로 좌석이 묶이고 행·번호 순으로 정렬된다")
    void list_groupsSeatsByTemplate() {
        SeatTemplate t1 = template(1L, "소극장 100석");
        SeatTemplate t2 = template(2L, "중극장 300석");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.MANAGER)));
        when(seatTemplateRepository.findActive()).thenReturn(List.of(t1, t2));
        when(templateSeatRepository.findByTemplateIdIn(List.of(1L, 2L))).thenReturn(List.of(
                seat(t1, "A-02", "A", 2, "R"),
                seat(t2, "B-01", "B", 1, "S"),
                seat(t1, "A-01", "A", 1, "R")));

        SeatTemplateListResponseDTO result = service.listSeatTemplate(1L);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).seats()).extracting("seatCode")
                .containsExactly("A-01", "A-02");     // 번호순 정렬
        assertThat(result.items().get(1).seats()).hasSize(1);
    }

    @Test
    @DisplayName("활성 템플릿이 없으면 빈 목록을 돌려주고 좌석은 조회하지 않는다")
    void list_empty() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN)));
        when(seatTemplateRepository.findActive()).thenReturn(List.of());

        assertThat(service.listSeatTemplate(1L).items()).isEmpty();
        verify(templateSeatRepository, never()).findByTemplateIdIn(any());
    }

    @Test
    @DisplayName("일반 회원은 템플릿 목록을 조회할 수 없다")
    void list_memberDenied() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(Role.MEMBER)));

        assertThatThrownBy(() -> service.listSeatTemplate(1L))
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
