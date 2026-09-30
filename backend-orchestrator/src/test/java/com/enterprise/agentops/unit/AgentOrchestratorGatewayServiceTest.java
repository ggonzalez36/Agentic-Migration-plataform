package com.enterprise.agentops.unit;

import com.enterprise.agentops.application.dto.AgentExecutionResult;
import com.enterprise.agentops.application.dto.AgentTaskExecutionCommand;
import com.enterprise.agentops.domain.model.AgentTask;
import com.enterprise.agentops.domain.model.AuditLog;
import com.enterprise.agentops.domain.model.enums.AgentType;
import com.enterprise.agentops.domain.model.enums.MigrationPhase;
import com.enterprise.agentops.domain.model.enums.TaskStatus;
import com.enterprise.agentops.domain.repository.AgentTaskRepository;
import com.enterprise.agentops.domain.repository.AuditLogRepository;
import com.enterprise.agentops.infrastructure.adapter.out.agent.AgentOrchestratorGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: AgentOrchestratorGatewayService - Agent Invocation & Telemetry")
class AgentOrchestratorGatewayServiceTest {

    @Mock private RestClient restClient;
    @Mock private AgentTaskRepository taskRepository;
    @Mock private AuditLogRepository auditLogRepository;

    @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private RestClient.RequestBodySpec requestBodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private AgentOrchestratorGatewayService gatewayService;

    @BeforeEach
    void setUp() {
        gatewayService = new AgentOrchestratorGatewayService(restClient, taskRepository, auditLogRepository);
    }

    @Test
    @DisplayName("Should successfully execute sync task, record audit log, and return AgentExecutionResult")
    void shouldExecuteSyncTaskSuccessfully() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        AgentTask mockTask = AgentTask.builder()
                .id(taskId)
                .taskIdentifier("TEST_TASK")
                .status(TaskStatus.PENDING)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(mockTask));

        // Mock fluent RestClient calls
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/v1/graphs/legacy-migration/run")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        AgentOrchestratorGatewayService.LangGraphResponse mockResponse = new AgentOrchestratorGatewayService.LangGraphResponse(
                "thread-123",
                "run-456",
                "ckpt-789",
                "@Service public class AccountService {}",
                "Extracted 3 business rules",
                Map.of(),
                1200,
                400,
                1600,
                BigDecimal.valueOf(0.015),
                false,
                null
        );

        when(responseSpec.body(AgentOrchestratorGatewayService.LangGraphResponse.class)).thenReturn(mockResponse);

        AgentTaskExecutionCommand command = new AgentTaskExecutionCommand(
                projectId, taskId, "trace-abc", "span-xyz", "thread-123",
                AgentType.PLSQL_CODE_ANALYZER, MigrationPhase.DISCOVERY_AND_AST_ANALYSIS,
                "PROCEDURE test IS BEGIN NULL; END;", "TEST_PKG", Map.of()
        );

        AgentExecutionResult result = gatewayService.executeSync(command);

        assertNotNull(result);
        assertEquals(TaskStatus.SUCCEEDED, result.status());
        assertEquals("thread-123", result.threadId());
        assertEquals(1200, result.promptTokens());
        assertEquals(400, result.completionTokens());
        assertFalse(result.requiresHumanApproval());

        // Verificar que se persistió auditoría inmutable
        verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class));
    }
}
