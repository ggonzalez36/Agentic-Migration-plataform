package com.enterprise.agentops.domain.model.enums;

public enum TaskStatus {
    PENDING,
    DISPATCHED,
    PROCESSING,
    AWAITING_HUMAN_APPROVAL,
    APPROVED,
    REJECTED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
