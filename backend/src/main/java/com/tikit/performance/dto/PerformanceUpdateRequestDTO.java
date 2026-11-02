package com.tikit.performance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class PerformanceUpdateRequestDTO {

    @NotNull
    // 조회했을 때의 version
    private Long expectedVersion;

    @Size(min = 1, max = 200)
    private String title;

    @Size(min = 1, max = 30)
    private String genreCode;

    @Size(min = 1, max = 200)
    private String venueName;

    @Size(max = 100)
    private String organizerName;

    @Size(min = 1)
    private String description;

    private String posterUrl;
    private LocalDate startDate;
    private LocalDate endDate;

    @Min(1)
    private Short bookingLimitPerMember;
}
