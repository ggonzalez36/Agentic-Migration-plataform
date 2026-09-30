# ADR-002: Banking-Grade AgentOps, Observability, and Tamper-Evident Auditing

## Status
**Accepted** (March 2026)

## Context & Problem Statement
Under financial regulations (such as Basel III/IV, SOC2 Type II, DORA, and GDPR), autonomous code generation and automated architectural refactoring cannot operate as a "black box".
Regulators and internal enterprise risk committees require:
1. Complete transparency over why and how each line of legacy code was transformed.
2. An indisputable, tamper-evident audit record of prompts, responses, tool executions, and state transformations.
3. Strict cost governance (FinOps) to prevent uncontrolled token consumption and API budget exhaustion.

## Decision
We implement a dual-layer **AgentOps & Compliance Subsystem**:
1. **W3C Distributed Tracing Standard**:
   - Every user or batch migration request generates a root `TraceId`.
   - Every discrete node within the LangGraph state machine generates a child `SpanId`.
   - The Spring Boot orchestrator passes these IDs via HTTP headers and records them in SLF4J MDC, propagating them through all internal services and database logs.
2. **Cryptographic Non-Repudiation (SHA-256 Hashing)**:
   - For every incoming legacy code block and every generated output artifact, the system computes and stores a SHA-256 digest in `agent_audit_logs.state_hash`.
   - Any manual tampering with generated source files outside the orchestrator invalidates the verification hash.
3. **Granular FinOps Metering**:
   - Every agent execution explicitly records: `prompt_tokens`, `completion_tokens`, `total_tokens`, `latency_ms`, and `estimated_cost_usd` mapped to standard model billing tiers.
   - Metrics are exported to Prometheus/Micrometer with alerting thresholds for runaway token consumption.

## Consequences

### Positive
- Full auditability satisfying regulatory inquiries.
- Accurate per-project and per-application cost attribution.
- Instant root-cause identification when an agent fails or produces low-quality code.

### Negative / Trade-offs
- Increased storage footprint in PostgreSQL due to detailed audit log persistence (mitigated via partitioned tables and archiving strategies).
- Hashing overhead (negligible for sub-megabyte code snippets).
