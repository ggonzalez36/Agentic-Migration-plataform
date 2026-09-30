package com.enterprise.agentops.domain.repository;

import com.enterprise.agentops.domain.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByTraceIdOrderByRecordedAtAsc(String traceId);
    List<AuditLog> findByTaskIdOrderByRecordedAtAsc(UUID taskId);
    Page<AuditLog> findByProjectId(UUID projectId, Pageable pageable);
}
