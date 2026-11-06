
package com.tikit.performance.entity;

import com.tikit.auth.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "favorites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoritePerformance {

    @EmbeddedId
    private FavoritePerformanceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            insertable = false,
            updatable = false,
            nullable = false
    )
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "performance_id",
            insertable = false,
            updatable = false,
            nullable = false
    )
    private Performance performance;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;

    public FavoritePerformance(Long memberId, Long performanceId) {
        this.id = new FavoritePerformanceId(memberId, performanceId);
    }
}
