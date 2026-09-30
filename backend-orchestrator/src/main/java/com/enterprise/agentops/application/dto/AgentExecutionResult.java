package com.enterprise.agentops.application.dto;

import com.enterprise.agentops.domain.model.enums.TaskStatus;

import java.math.BigDecimal;
import java.util.Map;

public record AgentExecutionResult(
        String threadId,
        String runId,
        String checkpointId,
        TaskStatus status,
        String generatedCode,
        String analysisSummary,
        Map<String, Object> stateOutput,
        int promptTokens,
        int completionTokens,
        long executionDurationMs,
        BigDecimal estimatedCostUsd,
        boolean requiresHumanApproval,
        String approvalPromptMessage,
        String errorMessage
) {
    public static AgentExecutionResult failure(String threadId, String error, long durationMs) {
        return new AgentExecutionResult(
                threadId, null, null, TaskStatus.FAILED, null, null,
                Map.of(), 0, 0, durationMs, BigDecimal.ZERO, false, null, error
        );
    }
}
