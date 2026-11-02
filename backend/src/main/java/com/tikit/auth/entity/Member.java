package com.tikit.auth.entity;

import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.common.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@EntityListeners(AuditingEntityListener.class)
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = false;

    @Column(name = "customer_key", nullable = false, unique = true, length = 100)
    private String customerKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    public static Member create(String email, String encodedPw, String realName, Role role) {
        Member member = new Member();
        member.email = email.trim().toLowerCase(Locale.ROOT);
        member.passwordHash = encodedPw;
        member.name = realName;
        member.role = role;
        member.customerKey = UUID.randomUUID().toString();
        return member;
    }

    // 회원 이름 변경
    public void changeName(String newName){
        this.name = newName;
    }

    public void suspend() {
        validateStatusChange(MemberStatus.ACTIVE);
        this.status = MemberStatus.SUSPENDED;
    }

    public void resume() {
        validateStatusChange(MemberStatus.SUSPENDED);
        this.status = MemberStatus.ACTIVE;
    }

    private void validateStatusChange(MemberStatus expectedStatus) {
        if (role == Role.ADMIN) {
            throw new MemberStatusChangeException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        if (status != expectedStatus) {
            throw new MemberStatusChangeException(ErrorCode.STATE_CONFLICT);
        }
    }

}
