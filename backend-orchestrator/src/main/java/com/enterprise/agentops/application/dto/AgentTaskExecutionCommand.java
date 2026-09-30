package com.enterprise.agentops.application.dto;

import com.enterprise.agentops.domain.model.enums.AgentType;
import com.enterprise.agentops.domain.model.enums.MigrationPhase;

import java.util.Map;
import java.util.UUID;

public record AgentTaskExecutionCommand(
        UUID projectId,
        UUID taskId,
        String traceId,
        String spanId,
        String threadId,
        AgentType agentType,
        MigrationPhase phase,
        String legacyCodeSnippet,
        String sourceObjectName, // ej: "PKG_ACCOUNT_BILLING.prc_close_month"
        Map<String, Object> contextualParameters
) {}
