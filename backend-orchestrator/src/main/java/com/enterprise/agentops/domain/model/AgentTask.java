package com.enterprise.agentops.domain.model;

import com.enterprise.agentops.domain.model.enums.AgentType;
import com.enterprise.agentops.domain.model.enums.MigrationPhase;
import com.enterprise.agentops.domain.model.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad que representa la ejecución atómica o compuesta de un Agente de IA dentro del grafo.
 * Almacena métricas de observabilidad AgentOps (tokens, latencia, coste, traces).
 */
@Entity
@Table(name = "agent_tasks", indexes = {
        @Index(name = "idx_task_project_id", columnList = "project_id"),
        @Index(name = "idx_task_trace_id", columnList = "trace_id"),
        @Index(name = "idx_task_thread_id", columnList = "thread_id"),
        @Index(name = "idx_task_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MigrationProject project;

    @Column(name = "task_identifier", nullable = false, length = 120)
    private String taskIdentifier; // ej: "AST_PARSER_PKG_TRANSACTIONS"

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 40)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 40)
    private MigrationPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    // --- AgentOps / LangGraph Tracing & Correlation ---
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "span_id", length = 64)
    private String spanId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "run_id", length = 128)
    private String runId;

    @Column(name = "checkpoint_id", length = 128)
    private String checkpointId;

    @Column(name = "model_name", length = 64)
    private String modelName; // ej: "claude-3-7-sonnet@20250219", "gemini-1.5-pro"

    // --- Métricas de consumo y rendimiento (AgentOps) ---
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "estimated_cost_usd", precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    // --- Carga útil y diagnósticos ---
    @Column(name = "input_payload", columnDefinition = "TEXT")
    private String inputPayload;

    @Column(name = "output_payload", columnDefinition = "TEXT")
    private String outputPayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void markStarted() {
        this.status = TaskStatus.PROCESSING;
        this.startedAt = Instant.now();
    }

    public void markSucceeded(String output, int promptTokens, int completionTokens, long latencyMs, BigDecimal cost) {
        this.status = TaskStatus.SUCCEEDED;
        this.outputPayload = output;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.latencyMs = latencyMs;
        this.estimatedCostUsd = cost;
        this.completedAt = Instant.now();
    }

    public void markFailed(String error, long latencyMs) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = error;
        this.latencyMs = latencyMs;
        this.completedAt = Instant.now();
    }

    public void markAwaitingHumanApproval(String checkpointId) {
        this.status = TaskStatus.AWAITING_HUMAN_APPROVAL;
        this.checkpointId = checkpointId;
    }
}
