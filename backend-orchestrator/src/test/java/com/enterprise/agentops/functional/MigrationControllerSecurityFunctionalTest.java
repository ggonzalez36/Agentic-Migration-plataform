package com.enterprise.agentops.functional;

import com.enterprise.agentops.application.dto.AgentExecutionResult;
import com.enterprise.agentops.application.port.out.AgentOrchestratorGateway;
import com.enterprise.agentops.domain.model.AgentTask;
import com.enterprise.agentops.domain.model.MigrationProject;
import com.enterprise.agentops.domain.model.enums.AgentType;
import com.enterprise.agentops.domain.model.enums.MigrationPhase;
import com.enterprise.agentops.domain.model.enums.ProjectStatus;
import com.enterprise.agentops.domain.model.enums.TaskStatus;
import com.enterprise.agentops.domain.repository.AgentTaskRepository;
import com.enterprise.agentops.domain.repository.MigrationProjectRepository;
import com.enterprise.agentops.infrastructure.adapter.in.rest.MigrationController;
import com.enterprise.agentops.infrastructure.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;

@WebMvcTest(MigrationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("Functional Test: MigrationController & Zero-Trust RBAC Security")
class MigrationControllerSecurityFunctionalTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JpaMetamodelMappingContext jpaMappingContext;
    @MockBean private AgentOrchestratorGateway agentGateway;
    @MockBean private MigrationProjectRepository projectRepository;
    @MockBean private AgentTaskRepository taskRepository;

    private static final String ARCHITECT_KEY = "key-enterprise-architect-secops";
    private static final String OPERATOR_KEY = "key-operator-cicd-runner";

    @Test
    @DisplayName("Should return 403 Forbidden when request lacks authentication API key")
    void shouldDenyUnauthenticatedRequest() throws Exception {
        UUID projectId = UUID.randomUUID();
        MigrationController.StartUnitMigrationRequest request = new MigrationController.StartUnitMigrationRequest(
                projectId, "PKG_ACCOUNT", "PROCEDURE test...", Map.of()
        );

        mockMvc.perform(post("/migrations/units/dispatch-async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow OPERATOR to dispatch asynchronous unit migration")
    void shouldAllowOperatorToDispatchUnitMigration() throws Exception {
        UUID projectId = UUID.randomUUID();
        MigrationProject project = MigrationProject.builder().id(projectId).name("Ledger").build();
        AgentTask task = AgentTask.builder().id(UUID.randomUUID()).project(project).taskIdentifier("PKG").build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(taskRepository.save(any(AgentTask.class))).thenReturn(task);
        when(agentGateway.executeAsync(any())).thenReturn(CompletableFuture.completedFuture(
                new AgentExecutionResult("t", "r", "c", TaskStatus.SUCCEEDED, "code", "sum", Map.of(), 10, 10, 100, BigDecimal.ZERO, false, null, null)
        ));

        MigrationController.StartUnitMigrationRequest request = new MigrationController.StartUnitMigrationRequest(
                projectId, "PKG_ACCOUNT", "PROCEDURE test...", Map.of()
        );

        mockMvc.perform(post("/migrations/units/dispatch-async")
                        .header("X-API-KEY", OPERATOR_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Should deny OPERATOR and require ARCHITECT for Human-in-the-Loop checkpoint approval")
    void shouldDenyOperatorForCheckpointApproval() throws Exception {
        UUID taskId = UUID.randomUUID();
        MigrationController.ApproveCheckpointRequest request = new MigrationController.ApproveCheckpointRequest(
                "thread-1", "ckpt-1", true, "LGTM"
        );

        // Operador intenta aprobar -> 403 Forbidden
        mockMvc.perform(post("/migrations/tasks/" + taskId + "/approve")
                        .header("X-API-KEY", OPERATOR_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow ARCHITECT to approve Human-in-the-Loop checkpoint")
    void shouldAllowArchitectForCheckpointApproval() throws Exception {
        UUID taskId = UUID.randomUUID();
        MigrationProject project = MigrationProject.builder().id(UUID.randomUUID()).name("Ledger").build();
        AgentTask task = AgentTask.builder().id(taskId).project(project).taskIdentifier("PKG").build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(agentGateway.resumeFromCheckpoint(any())).thenReturn(
                new AgentExecutionResult("thread-1", "run-1", "ckpt-1", TaskStatus.APPROVED, "code", "sum", Map.of(), 10, 10, 100, BigDecimal.ZERO, false, null, null)
        );

        MigrationController.ApproveCheckpointRequest request = new MigrationController.ApproveCheckpointRequest(
                "thread-1", "ckpt-1", true, "Approved for production merge"
        );

        // Arquitecto aprueba -> 200 OK
        mockMvc.perform(post("/migrations/tasks/" + taskId + "/approve")
                        .header("X-API-KEY", ARCHITECT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
