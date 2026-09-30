package com.enterprise.agentops.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "enterprise.agentops.engine")
public class AgentClientProperties {
    private String baseUrl = "http://localhost:8000";
    private String apiKey = "dev-agentops-internal-key";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(180);
    private int maxRetries = 3;
}
