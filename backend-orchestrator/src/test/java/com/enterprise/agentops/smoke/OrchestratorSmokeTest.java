package com.enterprise.agentops.smoke;

import com.enterprise.agentops.domain.repository.MigrationProjectRepository;
import com.enterprise.agentops.infrastructure.adapter.in.rest.MigrationController;
import com.enterprise.agentops.infrastructure.adapter.out.agent.AgentOrchestratorGatewayService;
import com.enterprise.agentops.infrastructure.security.DataMaskingService;
import com.enterprise.agentops.infrastructure.telemetry.AgentOpsMetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Smoke Test: Application Context, Core Beans & Health Actuator")
class OrchestratorSmokeTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Verify Spring context loads and all mission-critical beans are initialized")
    void contextLoadsAndCoreBeansArePresent() {
        assertNotNull(applicationContext.getBean(MigrationController.class), "MigrationController bean must be loaded");
        assertNotNull(applicationContext.getBean(AgentOrchestratorGatewayService.class), "AgentOrchestratorGatewayService bean must be loaded");
        assertNotNull(applicationContext.getBean(DataMaskingService.class), "DataMaskingService bean must be loaded");
        assertNotNull(applicationContext.getBean(AgentOpsMetricsService.class), "AgentOpsMetricsService bean must be loaded");
        assertNotNull(applicationContext.getBean(MigrationProjectRepository.class), "MigrationProjectRepository bean must be loaded");
    }

    @Test
    @DisplayName("Verify Actuator Health endpoint is publicly accessible and returns UP status")
    void actuatorHealthEndpointShouldReturnUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
