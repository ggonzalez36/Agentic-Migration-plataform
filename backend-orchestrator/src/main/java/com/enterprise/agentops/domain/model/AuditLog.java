package com.enterprise.agentops.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro inmutable de auditoría y telemetría de nivel bancario (AgentOps Audit Log).
 * Diseñado para compliance, trazabilidad determinista de decisiones de IA y cálculo de SLOs.
 */
@Entity
@Table(name = "agent_audit_logs", indexes = {
        @Index(name = "idx_audit_trace_id", columnList = "trace_id"),
        @Index(name = "idx_audit_project_id", columnList = "project_id"),
        @Index(name = "idx_audit_task_id", columnList = "task_id"),
        @Index(name = "idx_audit_node_name", columnList = "node_name"),
        @Index(name = "idx_audit_recorded_at", columnList = "recorded_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "task_id")
    private UUID taskId;

    // --- Distributed Tracing (W3C / OpenTelemetry standard) ---
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 64)
    private String spanId;

    @Column(name = "parent_span_id", length = 64)
    private String parentSpanId;

    // --- LangGraph Execution Context ---
    @Column(name = "node_name", nullable = false, length = 80)
    private String nodeName; // ej: "ast_parser_node", "spring_codegen_node", "qa_validator_node"

    @Column(name = "action", nullable = false, length = 80)
    private String action; // ej: "NODE_ENTER", "TOOL_CALLED", "CHECKPOINT_SAVED", "GATEWAY_TIMEOUT"

    @Column(name = "agent_model", length = 64)
    private String agentModel;

    @Column(name = "execution_status", nullable = false, length = 32)
    private String executionStatus; // "SUCCESS", "FAILURE", "INTERRUPTED", "HUMAN_OVERRIDE"

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "tokens_consumed")
    private Integer tokensConsumed;

    // --- Hash de integridad y Resumen de Estado ---
    @Column(name = "state_hash", length = 64)
    private String stateHash; // SHA-256 del estado de entrada/salida para no-repudio

    @Column(name = "payload_summary", columnDefinition = "TEXT")
    private String payloadSummary;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "actor", length = 100)
    @Builder.Default
    private String actor = "AGENTOPS_GATEWAY";
}
