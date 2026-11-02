package com.tikit.performance.dto;

import com.tikit.performance.entity.PerformanceSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class PerformanceCreateRequestDTO {

    @NotNull
    //MANUAL 또는 KOPIS
    private PerformanceSource source;

    @Size(max = 30)
    // KOPIS일 때만 사용
    private String kopisId;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 30)
    private String genreCode;

    @NotBlank
    @Size(max = 200)
    private String venueName;

    @Size(max = 100)
    private String organizerName;

    @NotBlank
    private String description;

    private String posterUrl;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @Min(1)
    private Short bookingLimitPerMember;
}
