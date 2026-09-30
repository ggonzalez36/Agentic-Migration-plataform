package com.enterprise.agentops.application.port.out;

import com.enterprise.agentops.application.dto.AgentExecutionResult;
import com.enterprise.agentops.application.dto.AgentTaskExecutionCommand;
import com.enterprise.agentops.application.dto.HumanApprovalCommand;

import java.util.concurrent.CompletableFuture;

/**
 * Puerto de salida para la comunicación con el motor de grafos de agentes (LangGraph).
 * Soporta invocaciones sincrónicas bloqueantes, asincrónicas no bloqueantes y resolución de HITL.
 */
public interface AgentOrchestratorGateway {

    /**
     * Envía una tarea de migración al grafo de agentes de forma síncrona.
     */
    AgentExecutionResult executeSync(AgentTaskExecutionCommand command);

    /**
     * Envía una tarea de migración de forma asíncrona devolviendo un CompletableFuture.
     */
    CompletableFuture<AgentExecutionResult> executeAsync(AgentTaskExecutionCommand command);

    /**
     * Reanuda un flujo interrumpido por un Checkpoint Human-in-the-Loop (HITL).
     */
    AgentExecutionResult resumeFromCheckpoint(HumanApprovalCommand command);
}
