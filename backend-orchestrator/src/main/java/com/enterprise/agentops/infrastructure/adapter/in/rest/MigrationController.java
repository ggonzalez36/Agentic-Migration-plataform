package com.enterprise.agentops.infrastructure.adapter.in.rest;

import com.enterprise.agentops.application.dto.AgentExecutionResult;
import com.enterprise.agentops.application.dto.AgentTaskExecutionCommand;
import com.enterprise.agentops.application.dto.HumanApprovalCommand;
import com.enterprise.agentops.application.port.out.AgentOrchestratorGateway;
import com.enterprise.agentops.domain.model.AgentTask;
import com.enterprise.agentops.domain.model.MigrationProject;
import com.enterprise.agentops.domain.model.enums.AgentType;
import com.enterprise.agentops.domain.model.enums.MigrationPhase;
import com.enterprise.agentops.domain.model.enums.ProjectStatus;
import com.enterprise.agentops.domain.repository.AgentTaskRepository;
import com.enterprise.agentops.domain.repository.MigrationProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/migrations")
@RequiredArgsConstructor
public class MigrationController {

    private final AgentOrchestratorGateway agentGateway;
    private final MigrationProjectRepository projectRepository;
    private final AgentTaskRepository taskRepository;

    public record StartUnitMigrationRequest(
            @NotNull UUID projectId,
            @NotBlank String sourceObjectName,
            @NotBlank String legacyPlSqlCode,
            Map<String, Object> parameters
    ) {}

    public record ApproveCheckpointRequest(
            @NotBlank String threadId,
            @NotBlank String checkpointId,
            boolean approved,
            String architectNotes
    ) {}

    @PostMapping("/units/dispatch-async")
    public ResponseEntity<Map<String, Object>> dispatchUnitMigrationAsync(@RequestBody @Valid StartUnitMigrationRequest request) {
        MigrationProject project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));

        String traceId = UUID.randomUUID().toString();

        AgentTask task = AgentTask.builder()
                .project(project)
                .taskIdentifier("MIGRATE_" + request.sourceObjectName())
                .agentType(AgentType.PLSQL_CODE_ANALYZER)
                .phase(MigrationPhase.DISCOVERY_AND_AST_ANALYSIS)
                .traceId(traceId)
                .inputPayload(request.legacyPlSqlCode())
                .build();

        AgentTask savedTask = taskRepository.save(task);

        AgentTaskExecutionCommand command = new AgentTaskExecutionCommand(
                project.getId(),
                savedTask.getId(),
                traceId,
                UUID.randomUUID().toString().substring(0, 16),
                UUID.randomUUID().toString(),
                AgentType.PLSQL_CODE_ANALYZER,
                MigrationPhase.DISCOVERY_AND_AST_ANALYSIS,
                request.legacyPlSqlCode(),
                request.sourceObjectName(),
                request.parameters()
        );

        // Despacho asíncrono
        CompletableFuture<AgentExecutionResult> futureResult = agentGateway.executeAsync(command);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Migración de unidad enviada al orquestador de agentes",
                "taskId", savedTask.getId(),
                "traceId", traceId
        ));
    }

    @PostMapping("/tasks/{taskId}/approve")
    public ResponseEntity<AgentExecutionResult> approveCheckpoint(
            @PathVariable UUID taskId,
            @RequestBody @Valid ApproveCheckpointRequest request) {

        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));

        HumanApprovalCommand approvalCommand = new HumanApprovalCommand(
                task.getProject().getId(),
                task.getId(),
                request.threadId(),
                request.checkpointId(),
                request.approved(),
                request.architectNotes(),
                Map.of()
        );

        AgentExecutionResult result = agentGateway.resumeFromCheckpoint(approvalCommand);
        return ResponseEntity.ok(result);
    }
}
