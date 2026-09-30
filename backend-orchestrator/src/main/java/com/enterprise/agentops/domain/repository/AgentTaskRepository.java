package com.enterprise.agentops.domain.repository;

import com.enterprise.agentops.domain.model.AgentTask;
import com.enterprise.agentops.domain.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {
    List<AgentTask> findByProjectId(UUID projectId);
    List<AgentTask> findByStatus(TaskStatus status);
    Optional<AgentTask> findByThreadIdAndRunId(String threadId, String runId);
    Optional<AgentTask> findByTraceId(String traceId);
}
