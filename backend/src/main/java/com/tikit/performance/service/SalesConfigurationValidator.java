package com.tikit.performance.service;

import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.GradePriceDTO;
import com.tikit.performance.dto.SessionConfigurationDTO;
import com.tikit.performance.dto.request.SalesConfigurationSaveRequestDTO;
import com.tikit.performance.exception.PerformanceException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SalesConfigurationValidator {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private SalesConfigurationValidator(){}

    //판매설정 요청 검증구현
    public static void validate(SalesConfigurationSaveRequestDTO request,
                                LocalDate performanceStart, LocalDate performanceEnd,
                                Set<Long> existingSessionIds,
                                Map<Long, Set<String>> gradesByTemplate){

        Set<Long> seenSessionIds = new HashSet<>();
        Set<Instant> seenStarts = new HashSet<>();

        for(SessionConfigurationDTO s : request.sessions()){
            if(s.sessionId()!=null){
                require(existingSessionIds.contains(s.sessionId()));
                require(seenSessionIds.add(s.sessionId()));
            }
            require(seenStarts.add(s.startsAt().toInstant()));
            LocalDate startDate = s.startsAt().atZoneSameInstant(KST).toLocalDate();
            require(!startDate.isBefore(performanceStart) && !startDate.isAfter(performanceEnd));

            require(s.salesOpenAt().isBefore(s.salesCloseAt()));
            require(!s.salesCloseAt().isAfter(s.startsAt()));
            require(!s.cancellationDeadline().isAfter(s.startsAt()));

            Set<String> templateGrades = gradesByTemplate.get(s.seatTemplateId());
            require(templateGrades != null);

            Set<String> priced = new HashSet<>();
            for(GradePriceDTO p: s.gradePrices()){
                require(priced.add(p.grade()));
            }
            require(priced.equals(templateGrades));
        }
    }

    private static void require(boolean condition){
        if(!condition){
            throw new PerformanceException(ErrorCode.INVALID_SALES_CONFIGURATION);
        }
    }
}
