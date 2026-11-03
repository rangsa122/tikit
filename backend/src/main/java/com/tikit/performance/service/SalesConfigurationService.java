package com.tikit.performance.service;

import com.tikit.performance.dto.SeatTemplateListResponseDTO;
import com.tikit.performance.dto.SeatTemplateResponseDTO;
import com.tikit.performance.dto.SeatTemplateSeatDTO;
import com.tikit.performance.entity.SeatTemplate;
import com.tikit.performance.entity.TemplateSeat;
import com.tikit.performance.repository.SeatTemplateRepository;
import com.tikit.seat.repository.TemplateSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesConfigurationService {

    private final PerformanceAccessChecker accessChecker;
    private final SeatTemplateRepository seatTemplateRepository;
    private final TemplateSeatRepository templateSeatRepository;

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

}
