package com.tikit.admin.entity;

import com.tikit.auth.entity.MemberStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_member_id", nullable = false)
    private Long actorMemberId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    private Map<String, String> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    private Map<String, String> afterState;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuditLog memberStatusChange(Long actorId, Long memberId, String reason,
                                              MemberStatus before, MemberStatus after) {
        AuditLog log = new AuditLog();
        log.actorMemberId = actorId;
        log.action = after == MemberStatus.SUSPENDED ? "MEMBER_SUSPEND" : "MEMBER_RESUME";
        log.targetType = "MEMBER";
        log.targetId = memberId;
        log.reason = reason.strip();
        log.beforeState = Map.of("status", before.name());
        log.afterState = Map.of("status", after.name());
        return log;
    }
}
