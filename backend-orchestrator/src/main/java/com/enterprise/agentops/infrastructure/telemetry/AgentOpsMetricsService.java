package com.enterprise.agentops.infrastructure.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de métricas personalizadas para FinOps y AgentOps.
 * Exporta telemetría a Prometheus / OpenTelemetry Collector vía Micrometer.
 */
@Service
public class AgentOpsMetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter totalSecurityViolations;
    private final AtomicInteger pendingHitlCheckpoints;

    public AgentOpsMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.totalSecurityViolations = Counter.builder("agentops.security.violations.total")
                .description("Total de intentos de prompt injection o accesos no autorizados interceptados")
                .register(meterRegistry);

        this.pendingHitlCheckpoints = meterRegistry.gauge(
                "agentops.hitl.checkpoints.pending",
                new AtomicInteger(0)
        );
    }

    public void recordTokenUsage(String model, int promptTokens, int completionTokens, BigDecimal costUsd) {
        Counter.builder("agentops.tokens.consumed.total")
                .tag("model", model)
                .tag("token_type", "prompt")
                .register(meterRegistry)
                .increment(promptTokens);

        Counter.builder("agentops.tokens.consumed.total")
                .tag("model", model)
                .tag("token_type", "completion")
                .register(meterRegistry)
                .increment(completionTokens);

        if (costUsd != null) {
            Counter.builder("agentops.cost.usd.total")
                    .tag("model", model)
                    .register(meterRegistry)
                    .increment(costUsd.doubleValue());
        }
    }

    public void recordExecutionTime(String agentType, String status, long durationMs) {
        Timer.builder("agentops.task.execution.duration")
                .tag("agent_type", agentType)
                .tag("status", status)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    public void incrementPendingHitl() {
        if (pendingHitlCheckpoints != null) {
            pendingHitlCheckpoints.incrementAndGet();
        }
    }

    public void decrementPendingHitl() {
        if (pendingHitlCheckpoints != null && pendingHitlCheckpoints.get() > 0) {
            pendingHitlCheckpoints.decrementAndGet();
        }
    }

    public void recordSecurityViolation() {
        totalSecurityViolations.increment();
    }
}
