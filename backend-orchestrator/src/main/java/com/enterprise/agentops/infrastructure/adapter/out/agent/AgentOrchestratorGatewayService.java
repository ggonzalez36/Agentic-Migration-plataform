package com.enterprise.agentops.infrastructure.adapter.out.agent;

import com.enterprise.agentops.application.dto.AgentExecutionResult;
import com.enterprise.agentops.application.dto.AgentTaskExecutionCommand;
import com.enterprise.agentops.application.dto.HumanApprovalCommand;
import com.enterprise.agentops.application.port.out.AgentOrchestratorGateway;
import com.enterprise.agentops.domain.model.AgentTask;
import com.enterprise.agentops.domain.model.AuditLog;
import com.enterprise.agentops.domain.model.enums.TaskStatus;
import com.enterprise.agentops.domain.repository.AgentTaskRepository;
import com.enterprise.agentops.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio Gateway de grado empresarial que orquesta la comunicación con el motor Python/LangGraph.
 * Integra observabilidad AgentOps, captura de auditoría para compliance bancario y tolerancia a fallos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestratorGatewayService implements AgentOrchestratorGateway {

    private final RestClient agentEngineRestClient;
    private final AgentTaskRepository agentTaskRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public AgentExecutionResult executeSync(AgentTaskExecutionCommand command) {
        log.info("[AgentOps] Despachando tarea síncrona: taskId={}, project={}, type={}",
                command.taskId(), command.projectId(), command.agentType());

        long startTime = System.currentTimeMillis();
        String traceId = command.traceId() != null ? command.traceId() : UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString().substring(0, 16);

        // 1. Audit Log: Pre-invocación
        recordAudit(command.projectId(), command.taskId(), traceId, spanId,
                command.agentType().name(), "DISPATCH_STARTED", "PROCESSING",
                0L, 0, computeHash(command.legacyCodeSnippet()),
                "Iniciando ejecución de grafo LangGraph para: " + command.sourceObjectName(), null);

        // 2. Actualizar estado de la tarea a PROCESSING
        agentTaskRepository.findById(command.taskId()).ifPresent(AgentTask::markStarted);

        try {
            // Contrato de petición hacia LangGraph
            Map<String, Object> requestPayload = Map.of(
                    "thread_id", command.threadId() != null ? command.threadId() : UUID.randomUUID().toString(),
                    "trace_id", traceId,
                    "span_id", spanId,
                    "agent_type", command.agentType().name(),
                    "phase", command.phase().name(),
                    "source_object_name", command.sourceObjectName(),
                    "legacy_code", command.legacyCodeSnippet(),
                    "context", command.contextualParameters() != null ? command.contextualParameters() : Map.of()
            );

            // 3. Invocación HTTP con RestClient
            LangGraphResponse response = agentEngineRestClient.post()
                    .uri("/api/v1/graphs/legacy-migration/run")
                    .body(requestPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        String errorBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("[AgentOps] Error 4xx del motor LangGraph: {}", errorBody);
                        throw new RuntimeException("Error cliente en LangGraph: " + errorBody);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        String errorBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("[AgentOps] Error 5xx en LangGraph: {}", errorBody);
                        throw new RuntimeException("Fallo interno en LangGraph Agent Engine: " + errorBody);
                    })
                    .body(LangGraphResponse.class);

            long duration = System.currentTimeMillis() - startTime;

            if (response == null) {
                throw new IllegalStateException("Respuesta nula recibida de LangGraph Engine");
            }

            // 4. Mapear resultado y persistir telemetría
            TaskStatus status = response.requiresHumanApproval()
                    ? TaskStatus.AWAITING_HUMAN_APPROVAL
                    : TaskStatus.SUCCEEDED;

            persistTaskSuccess(command.taskId(), response, duration);

            recordAudit(command.projectId(), command.taskId(), traceId, spanId,
                    command.agentType().name(), "DISPATCH_COMPLETED", status.name(),
                    duration, response.totalTokens(), computeHash(response.generatedCode()),
                    "Ejecución completada exitosamente. Checkpoint: " + response.checkpointId(), null);

            return new AgentExecutionResult(
                    response.threadId(),
                    response.runId(),
                    response.checkpointId(),
                    status,
                    response.generatedCode(),
                    response.analysisSummary(),
                    response.stateOutput(),
                    response.promptTokens(),
                    response.completionTokens(),
                    duration,
                    response.estimatedCostUsd(),
                    response.requiresHumanApproval(),
                    response.approvalMessage(),
                    null
            );

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[AgentOps] Excepción durante la invocación del agente: {}", ex.getMessage(), ex);

            agentTaskRepository.findById(command.taskId())
                    .ifPresent(task -> task.markFailed(ex.getMessage(), duration));

            recordAudit(command.projectId(), command.taskId(), traceId, spanId,
                    command.agentType().name(), "DISPATCH_FAILED", "FAILURE",
                    duration, 0, null, "Error en invocación: " + ex.getMessage(), ex.toString());

            return AgentExecutionResult.failure(command.threadId(), ex.getMessage(), duration);
        }
    }

    @Override
    @Async("agentExecutionTaskExecutor")
    public CompletableFuture<AgentExecutionResult> executeAsync(AgentTaskExecutionCommand command) {
        log.info("[AgentOps] Despachando tarea asíncrona: taskId={}", command.taskId());
        return CompletableFuture.supplyAsync(() -> executeSync(command));
    }

    @Override
    @Transactional
    public AgentExecutionResult resumeFromCheckpoint(HumanApprovalCommand command) {
        log.info("[AgentOps] Reanudando Checkpoint HITL: threadId={}, checkpointId={}, approved={}",
                command.threadId(), command.checkpointId(), command.approved());

        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString().substring(0, 16);

        Map<String, Object> resumePayload = Map.of(
                "thread_id", command.threadId(),
                "checkpoint_id", command.checkpointId(),
                "approved", command.approved(),
                "architect_notes", command.architectNotes() != null ? command.architectNotes() : "",
                "manual_overrides", command.manualOverrides() != null ? command.manualOverrides() : Map.of()
        );

        try {
            LangGraphResponse response = agentEngineRestClient.post()
                    .uri("/api/v1/graphs/legacy-migration/resume")
                    .body(resumePayload)
                    .retrieve()
                    .body(LangGraphResponse.class);

            long duration = System.currentTimeMillis() - startTime;
            if (response == null) {
                throw new IllegalStateException("Respuesta nula al reanudar checkpoint");
            }

            TaskStatus status = command.approved() ? TaskStatus.APPROVED : TaskStatus.REJECTED;

            recordAudit(command.projectId(), command.taskId(), traceId, spanId,
                    "HUMAN_GATEKEEPER_CHECKPOINT", "CHECKPOINT_RESUMED", status.name(),
                    duration, response.totalTokens(), null,
                    "Decisión humana aplicada: " + (command.approved() ? "APROBADO" : "RECHAZADO"), null);

            return new AgentExecutionResult(
                    response.threadId(), response.runId(), response.checkpointId(),
                    status, response.generatedCode(), response.analysisSummary(),
                    response.stateOutput(), response.promptTokens(), response.completionTokens(),
                    duration, response.estimatedCostUsd(), false, null, null
            );
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[AgentOps] Fallo al reanudar checkpoint HITL: {}", ex.getMessage(), ex);
            return AgentExecutionResult.failure(command.threadId(), ex.getMessage(), duration);
        }
    }

    private void persistTaskSuccess(UUID taskId, LangGraphResponse response, long duration) {
        agentTaskRepository.findById(taskId).ifPresent(task -> {
            task.setRunId(response.runId());
            task.setThreadId(response.threadId());
            task.setCheckpointId(response.checkpointId());
            if (response.requiresHumanApproval()) {
                task.markAwaitingHumanApproval(response.checkpointId());
            } else {
                task.markSucceeded(
                        response.generatedCode() != null ? response.generatedCode() : response.analysisSummary(),
                        response.promptTokens(),
                        response.completionTokens(),
                        duration,
                        response.estimatedCostUsd()
                );
            }
        });
    }

    private void recordAudit(UUID projectId, UUID taskId, String traceId, String spanId,
                             String nodeName, String action, String status,
                             long durationMs, int tokens, String stateHash,
                             String summary, String errorDetails) {
        AuditLog audit = AuditLog.builder()
                .projectId(projectId)
                .taskId(taskId)
                .traceId(traceId)
                .spanId(spanId)
                .nodeName(nodeName)
                .action(action)
                .executionStatus(status)
                .latencyMs(durationMs)
                .tokensConsumed(tokens)
                .stateHash(stateHash)
                .payloadSummary(summary)
                .errorDetails(errorDetails)
                .recordedAt(Instant.now())
                .actor("AGENTOPS_ORCHESTRATOR")
                .build();
        auditLogRepository.save(audit);
    }

    private String computeHash(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    /**
     * DTO interno para deserializar el contrato devuelto por el servicio Python/FastAPI/LangGraph.
     */
    public record LangGraphResponse(
            String threadId,
            String runId,
            String checkpointId,
            String generatedCode,
            String analysisSummary,
            Map<String, Object> stateOutput,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            BigDecimal estimatedCostUsd,
            boolean requiresHumanApproval,
            String approvalMessage
    ) {}
}
