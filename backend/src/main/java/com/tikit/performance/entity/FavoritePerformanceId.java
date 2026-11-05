
package com.tikit.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FavoritePerformanceId implements Serializable {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;
}
