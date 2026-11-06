package com.tikit.performance.kopis;

import java.time.LocalDate;

//kopis 상세
public record KopisPerformanceDetail(
        //주석은 kopis의 원래 필드 이름
        String kopisId,         // mt20id
        String title,           // prfnm
        LocalDate startDate,    // prfpdfrom
        LocalDate endDate,      // prfpdto
        String venueName,       // fcltynm
        String posterUrl,       // poster
        String genreName,       // genrenm
        String story,           // sty (줄거리, 비어 있을 수 있음)
        String organizerName,   // entrpsnm (기획제작사)
        String cast,            // prfcast (출연진)
        String runtime,         // prfruntime
        String age,             // prfage
        String timeGuidance,    // dtguidance (공연시간 안내)
        String priceGuidance,   // pcseguidance (티켓가격)
        String state            // prfstate
) {
}
