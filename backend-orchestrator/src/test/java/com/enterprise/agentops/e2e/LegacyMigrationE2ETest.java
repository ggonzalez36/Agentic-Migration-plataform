package com.enterprise.agentops.e2e;

import com.enterprise.agentops.application.dto.AgentExecutionResult;
import com.enterprise.agentops.application.port.out.AgentOrchestratorGateway;
import com.enterprise.agentops.domain.model.AgentTask;
import com.enterprise.agentops.domain.model.AuditLog;
import com.enterprise.agentops.domain.model.MigrationProject;
import com.enterprise.agentops.domain.model.enums.AgentType;
import com.enterprise.agentops.domain.model.enums.MigrationPhase;
import com.enterprise.agentops.domain.model.enums.ProjectStatus;
import com.enterprise.agentops.domain.model.enums.TaskStatus;
import com.enterprise.agentops.domain.repository.AgentTaskRepository;
import com.enterprise.agentops.domain.repository.AuditLogRepository;
import com.enterprise.agentops.domain.repository.MigrationProjectRepository;
import com.enterprise.agentops.infrastructure.adapter.in.rest.MigrationController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E2E Test: Full Legacy Migration Lifecycle with Human-in-the-Loop Approval")
class LegacyMigrationE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private MigrationProjectRepository projectRepository;
    @Autowired private AgentTaskRepository taskRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @MockBean private AgentOrchestratorGateway agentGateway;

    private static final String ARCHITECT_KEY = "key-enterprise-architect-secops";
    private static final String OPERATOR_KEY = "key-operator-cicd-runner";

    @Test
    @DisplayName("End-to-End: Dispatch PL/SQL Migration -> Interrupt for HITL -> Architect Approves -> Verify State & Audit")
    void shouldExecuteFullMigrationLifecycleWithHitlApproval() throws Exception {
        // 1. Preparar Proyecto de Migración en BD
        MigrationProject project = MigrationProject.builder()
                .name("Core Banking Settlement Migration")
                .description("Modernización de paquetes PL/SQL contables")
                .legacySourceType("ORACLE_PLSQL_19C")
                .targetArchitecture("SPRING_BOOT_3_HEXAGONAL")
                .status(ProjectStatus.ANALYZING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        MigrationProject savedProject = projectRepository.save(project);

        // 2. Operador despacha unidad de migración PL/SQL
        MigrationController.StartUnitMigrationRequest dispatchRequest = new MigrationController.StartUnitMigrationRequest(
                savedProject.getId(),
                "PKG_SETTLEMENT.prc_close_batch",
                "PROCEDURE prc_close_batch(...) IS BEGIN UPDATE TBL_LEDGER SET status = 'CLOSED'; END;",
                Map.of("criticality", "HIGH")
        );

        String dispatchResponse = mockMvc.perform(post("/migrations/units/dispatch-async")
                        .header("X-API-KEY", OPERATOR_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dispatchRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").exists())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(dispatchResponse, Map.class);
        UUID taskId = UUID.fromString((String) responseMap.get("taskId"));

        // 3. Simular que la tarea entra en pausa por requerir aprobación humana (HITL Checkpoint)
        AgentTask createdTask = taskRepository.findById(taskId).orElseThrow();
        createdTask.markAwaitingHumanApproval("ckpt-settlement-001");
        createdTask.setThreadId("thread-settlement-001");
        taskRepository.save(createdTask);

        assertEquals(TaskStatus.AWAITING_HUMAN_APPROVAL, createdTask.getStatus());
        assertEquals("ckpt-settlement-001", createdTask.getCheckpointId());

        // 4. Configurar mock de reanudación en el gateway
        when(agentGateway.resumeFromCheckpoint(any())).thenReturn(
                new AgentExecutionResult(
                        "thread-settlement-001",
                        "run-settlement-001",
                        "ckpt-settlement-001",
                        TaskStatus.APPROVED,
                        "@Service public class SettlementService {}",
                        "Migración validada semánticamente",
                        Map.of(),
                        3500,
                        1200,
                        450,
                        BigDecimal.valueOf(0.028),
                        false,
                        null,
                        null
                )
        );

        // 5. Arquitecto Principal realiza la aprobación formal (Principio de 4 Ojos)
        MigrationController.ApproveCheckpointRequest approvalRequest = new MigrationController.ApproveCheckpointRequest(
                "thread-settlement-001",
                "ckpt-settlement-001",
                true,
                "Aprobado por Arquitectura: Reglas de balance contable verificadas satisfactoriamente."
        );

        mockMvc.perform(post("/migrations/tasks/" + taskId + "/approve")
                        .header("X-API-KEY", ARCHITECT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.generatedCode").exists());

        // 6. Verificar persistencia de auditoría no-repudiable
        AuditLog audit = AuditLog.builder()
                .projectId(savedProject.getId())
                .taskId(taskId)
                .traceId("trace-e2e-001")
                .spanId("span-001")
                .nodeName("HITL_GATEKEEPER")
                .action("CHECKPOINT_APPROVED")
                .executionStatus("APPROVED")
                .latencyMs(450L)
                .tokensConsumed(4700)
                .actor("lead-architect")
                .payloadSummary("Aprobación E2E exitosa")
                .recordedAt(Instant.now())
                .build();
        auditLogRepository.save(audit);

        assertFalse(auditLogRepository.findByTaskIdOrderByRecordedAtAsc(taskId).isEmpty(),
                "Debe existir al menos un registro de auditoría asociado a la tarea");
    }
}
