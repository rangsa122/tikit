package com.tikit.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_grade_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//회차별 등급 가격
public class SessionGradePrice {

    @EmbeddedId
    private SessionGradePriceId id;

    @MapsId("sessionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private PerformanceSession session;

    @Column(nullable = false)
    private long price;

    public static SessionGradePrice create(PerformanceSession session, String grade, long price) {
        SessionGradePrice p = new SessionGradePrice();
        p.id = new SessionGradePriceId(session.getId(), grade);
        p.session = session;
        p.price = price;
        return p;
    }
}