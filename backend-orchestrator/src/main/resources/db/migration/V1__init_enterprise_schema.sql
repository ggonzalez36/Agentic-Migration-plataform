-- ============================================================================
-- Enterprise AgentOps & Migration Platform Database Schema (PostgreSQL 15+)
-- Tier-1 Banking Compliance, Non-Repudiation, and Audit Tracing
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Tabla de Proyectos de Modernización
CREATE TABLE IF NOT EXISTS migration_projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    legacy_source_type VARCHAR(50) NOT NULL,
    target_architecture VARCHAR(50) NOT NULL,
    source_repository_url VARCHAR(512),
    target_repository_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'INITIALIZING',
    total_units_count INT NOT NULL DEFAULT 0,
    migrated_units_count INT NOT NULL DEFAULT 0,
    active_langgraph_thread_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_project_status CHECK (status IN (
        'INITIALIZING', 'ANALYZING', 'GENERATING', 'VALIDATING',
        'AWAITING_APPROVAL', 'COMPLETED', 'FAILED', 'SUSPENDED'
    ))
);

CREATE INDEX idx_project_status ON migration_projects (status);
CREATE INDEX idx_project_created_at ON migration_projects (created_at DESC);

-- 2. Tabla de Tareas de Agentes (Agent Execution & FinOps Telemetry)
CREATE TABLE IF NOT EXISTS agent_tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES migration_projects(id) ON DELETE CASCADE,
    task_identifier VARCHAR(120) NOT NULL,
    agent_type VARCHAR(40) NOT NULL,
    phase VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    trace_id VARCHAR(64),
    span_id VARCHAR(64),
    thread_id VARCHAR(128),
    run_id VARCHAR(128),
    checkpoint_id VARCHAR(128),
    model_name VARCHAR(64),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    latency_ms BIGINT,
    estimated_cost_usd NUMERIC(10, 6),
    retry_count INT DEFAULT 0,
    input_payload TEXT,
    output_payload TEXT,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_task_status CHECK (status IN (
        'PENDING', 'DISPATCHED', 'PROCESSING', 'AWAITING_HUMAN_APPROVAL',
        'APPROVED', 'REJECTED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'
    ))
);

CREATE INDEX idx_task_project_id ON agent_tasks (project_id);
CREATE INDEX idx_task_trace_id ON agent_tasks (trace_id);
CREATE INDEX idx_task_thread_id ON agent_tasks (thread_id);
CREATE INDEX idx_task_status ON agent_tasks (status);

-- 3. Tabla de Auditoría Inmutable de AgentOps (Cryptographic Non-Repudiation)
CREATE TABLE IF NOT EXISTS agent_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL,
    task_id UUID,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    node_name VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    agent_model VARCHAR(64),
    execution_status VARCHAR(32) NOT NULL,
    latency_ms BIGINT,
    tokens_consumed INT,
    state_hash VARCHAR(64), -- SHA-256 Digest del estado para cumplimiento normativo
    payload_summary TEXT,
    error_details TEXT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor VARCHAR(100) DEFAULT 'AGENTOPS_GATEWAY'
);

CREATE INDEX idx_audit_trace_id ON agent_audit_logs (trace_id);
CREATE INDEX idx_audit_project_id ON agent_audit_logs (project_id);
CREATE INDEX idx_audit_task_id ON agent_audit_logs (task_id);
CREATE INDEX idx_audit_node_name ON agent_audit_logs (node_name);
CREATE INDEX idx_audit_recorded_at ON agent_audit_logs (recorded_at DESC);
