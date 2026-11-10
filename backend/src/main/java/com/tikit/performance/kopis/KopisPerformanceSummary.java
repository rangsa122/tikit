package com.tikit.performance.kopis;

import java.time.LocalDate;

//kopis 목록응답 한건
public record KopisPerformanceSummary(
        //주석은 kopis의 원래 필드 이름
        String kopisId,      // mt20id
        String title,        // prfnm
        LocalDate startDate, // prfpdfrom
        LocalDate endDate,   // prfpdto
        String venueName,    // fcltynm
        String posterUrl,    // poster
        String area,         // area
        String genreName,    // genrenm
        String state         // prfstate
) {
}