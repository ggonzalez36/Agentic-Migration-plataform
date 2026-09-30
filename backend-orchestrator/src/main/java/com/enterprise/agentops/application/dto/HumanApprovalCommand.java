package com.enterprise.agentops.application.dto;

import java.util.Map;
import java.util.UUID;

public record HumanApprovalCommand(
        UUID projectId,
        UUID taskId,
        String threadId,
        String checkpointId,
        boolean approved,
        String architectNotes,
        Map<String, Object> manualOverrides
) {}
