package com.tikit.performance.entity;

import com.tikit.auth.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "performances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Performance {

    private static final short DEFAULT_BOOKING_LIMIT = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Member createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_code", nullable = false)
    private Genre genre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PerformanceSource source;

    @Column(name = "kopis_id", length = 30)
    private String kopisId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "venue_name", nullable = false, length = 200)
    private String venueName;

    @Column(name = "organizer_name", length = 100)
    private String organizerName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus publicationStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_status", nullable = false, length = 20)
    private SalesStatus salesStatus;

    @Column(name = "booking_limit_per_member", nullable = false)
    private short bookingLimitPerMember;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updateAt;

    public static Performance createManual(Member createdBy, Genre genre, String title,
                                           String venueName, String organizerName, String description, String posterUrl,
                                           LocalDate startDate, LocalDate endDate, Short bookingLimitPerMember) {
        return build(createdBy, genre, PerformanceSource.MANUAL, null, title, venueName,
                organizerName, description, posterUrl, startDate, endDate, bookingLimitPerMember);
    }

    public static Performance createFromKopis(Member createdBy, Genre genre, String kopisId,
                                              String title, String venueName, String organizerName, String description,
                                              String posterUrl, LocalDate startDate, LocalDate endDate, Short bookingLimitPerMember) {
        return build(createdBy, genre, PerformanceSource.KOPIS, kopisId, title, venueName,
                organizerName, description, posterUrl, startDate, endDate, bookingLimitPerMember);
    }

    private static Performance build(Member createdBy, Genre genre, PerformanceSource source,
                                     String kopisId, String title, String venueName, String organizerName,
                                     String description, String posterUrl, LocalDate startDate, LocalDate endDate,
                                     Short bookingLimitPerMember) {
        Performance p = new Performance();
        p.createdBy = createdBy;
        p.genre = genre;
        p.source = source;
        p.kopisId = kopisId;
        p.title = title;
        p.venueName = venueName;
        p.organizerName = organizerName;
        p.description = description;
        p.posterUrl = posterUrl;
        p.startDate = startDate;
        p.endDate = endDate;
        p.publicationStatus = PublicationStatus.DRAFT;
        p.visibility = Visibility.PRIVATE;
        p.salesStatus = SalesStatus.STOPPED;
        p.bookingLimitPerMember =
                (bookingLimitPerMember == null) ? DEFAULT_BOOKING_LIMIT : bookingLimitPerMember;
        return p;
    }

}
