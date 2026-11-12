package com.tikit.admin.repository;

import com.tikit.admin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Optional<AuditLog> findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
            String targetType, Long targetId, Collection<String> actions);
}
