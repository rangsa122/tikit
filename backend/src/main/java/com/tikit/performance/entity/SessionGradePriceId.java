package com.tikit.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class SessionGradePriceId implements Serializable {

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "grade", length = 20)
    private String grade;

    public SessionGradePriceId(Long sessionId, String grade) {
        this.sessionId = sessionId;
        this.grade = grade;
    }
}
