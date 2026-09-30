package com.enterprise.agentops.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuración dedicada para la auditoría de entidades JPA.
 * Aislada de OrchestratorApplication para no interferir con los test slices de WebMvc (@WebMvcTest).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {
}
