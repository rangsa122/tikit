package com.tikit.performance.dto.response;

import com.tikit.performance.entity.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PerformanceResponseDTO {

    private Long performanceId;
    private String genreCode;
    private String genreName;
    private PerformanceSource source;
    private String kopisId;
    private String title;
    private String venueName;
    private String organizerName;
    private String description;
    private String posterUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private PublicationStatus publicationStatus;
    private Visibility visibility;
    private SalesStatus salesStatus;
    private short bookingLimitPerMember;
    private Long createdBy;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static PerformanceResponseDTO from(Performance p) {
        return new PerformanceResponseDTO(
                p.getId(),
                p.getGenre().getCode(),
                p.getGenre().getName(),
                p.getSource(),
                p.getKopisId(),
                p.getTitle(),
                p.getVenueName(),
                p.getOrganizerName(),
                p.getDescription(),
                p.getPosterUrl(),
                p.getStartDate(),
                p.getEndDate(),
                p.getPublicationStatus(),
                p.getVisibility(),
                p.getSalesStatus(),
                p.getBookingLimitPerMember(),
                p.getCreatedBy().getId(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

}

