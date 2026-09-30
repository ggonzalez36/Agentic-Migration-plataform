# System Design Document: Enterprise Agentic Migration Platform

**Document Version:** 2.0.0 (Enterprise Financial Tier-1)  
**Classification:** Confidential / Architectural Blueprint  
**Authors:** Principal Software Architecture & Lead Security Engineering Team  

---

## 1. Executive Summary & Problem Scope

Global banking institutions face multi-billion dollar technical debt embedded in legacy relational database engines (principally **Oracle PL/SQL** stored procedures, packages, triggers, and DB2 SQL PL). These procedures encapsulate core transaction handling, financial ledger calculations, interest computations, and regulatory compliance checks.

Direct "lift-and-shift" migrations are prone to failure. This platform automates the reverse-engineering, decomposition, and transformation of monolithic PL/SQL into **Domain-Driven Design (DDD) Hexagonal Spring Boot 3.3+ (Java 21)** microservices, while enforcing strict banking controls:
- **Zero-Trust Cybersecurity & OWASP Top 10 for LLM Defense**
- **Deterministic Human-in-the-Loop (HITL) Checkpoints (Four-Eyes Principle)**
- **Cryptographic Non-Repudiation (SHA-256 State Hashing)**
- **Granular FinOps & AgentOps Telemetry (Token & Latency Tracking)**

---

## 2. C4 Architecture Model

### 2.1 Level 1: System Context Diagram

```mermaid
C4Context
    title System Context - Enterprise Agentic Migration Platform

    Person(architect, "Lead Enterprise Architect", "Authorizes checkpoints and reviews synthesized microservices")
    Person(operator, "DevOps / Migration Engineer", "Dispatches batch migrations and monitors pipelines")

    System(platform, "Agentic Migration Platform", "Orchestrates AST decomposition, agent workflows, code generation, and verification")

    System_Ext(legacyDB, "Legacy Database (Oracle 19c)", "Source repository of packages, triggers, and DDL")
    System_Ext(targetGit, "Corporate GitLab / GitHub", "Target destination for generated Spring Boot microservices")
    System_Ext(llmProvider, "Enterprise AI Gateway (Anthropic/Google)", "Foundation models (Claude 3.7 Sonnet / Gemini 1.5 Pro) via private endpoints")
    System_Ext(siem, "Corporate SIEM & Observability", "Prometheus, Grafana, OpenTelemetry, Splunk")

    Rel(operator, platform, "Submits migration jobs & configures projects", "HTTPS / REST")
    Rel(architect, platform, "Audits and approves HITL checkpoints", "HTTPS / Web UI")
    Rel(platform, legacyDB, "Extracts DDL, PL/SQL packages, and dependencies", "JDBC / Oracle Net")
    Rel(platform, targetGit, "Pushes validated microservice PRs", "SSH / Git API")
    Rel(platform, llmProvider, "Dispatches cognitive reasoning prompts", "mTLS / REST")
    Rel(platform, siem, "Streams AgentOps telemetry and security audit logs", "OTLP / Prometheus")
```

---

### 2.2 Level 2: Container Diagram

```mermaid
C4Container
    title Container Architecture - Decoupled Hybrid Ecosystem

    Person(user, "Enterprise Architect / Operator", "Web browser")

    Container(spa, "Modern Web Console", "React 18 / Vite / Tailwind", "Visualizes AST graph, live SSE execution traces, and HITL diff reviews")
    
    Container_Boundary(core_app, "Spring Boot Core Subsystem") {
        Container(orchestrator, "Core Orchestrator & Gateway", "Spring Boot 3.3 / Java 21", "Manages state, security (RBAC), persistence, RestClient gateway, and data masking")
        ContainerDb(postgres, "Enterprise Metadata Store", "PostgreSQL 16", "Stores projects, tasks, token metrics, and immutable audit ledgers")
    }

    Container_Boundary(agent_app, "Agentic Intelligence Subsystem") {
        Container(agent_engine, "LangGraph Agent Engine", "Python 3.11 / FastAPI", "Executes multi-agent state machines, guardrails, and AST parsing")
        ContainerDb(checkpoints, "State Checkpointer", "MemorySaver / Postgres Checkpointer", "Freezes execution threads during HITL pauses")
    }

    Rel(user, spa, "Interacts via", "HTTPS")
    Rel(spa, orchestrator, "API calls & SSE live streaming", "HTTPS / JSON")
    Rel(orchestrator, postgres, "Reads/writes metadata & audit logs", "JDBC / HikariCP")
    Rel(orchestrator, agent_engine, "Dispatches graph runs & checkpoint approvals", "mTLS / REST with W3C Headers")
    Rel(agent_engine, checkpoints, "Saves / restores thread snapshots", "Internal State IPC")
```

---

### 2.3 Level 3: Component Diagram (Spring Boot Core)

```mermaid
C4Component
    title Component Diagram - Spring Boot 3 Core Orchestrator

    Container_Boundary(api_boundary, "Infrastructure In (Web)") {
        Component(controller, "MigrationController", "REST Controller", "Exposes /migrations endpoints with Spring Security RBAC")
        Component(securityFilter, "ApiKeyAuthenticationFilter", "Security Filter", "Validates X-API-KEY / Bearer tokens and injects authorities")
    }

    Container_Boundary(security_boundary, "Infrastructure Security") {
        Component(dataMasker, "DataMaskingService", "Regex Sanitizer", "Redacts PCI-DSS PANs, SSNs, and DB credentials before logging")
    }

    Container_Boundary(domain_boundary, "Application & Domain Core") {
        Component(usecase, "MigrationOrchestrationService", "Service Layer", "Coordinates project lifecycles and transactional operations")
        Component(gatewayPort, "AgentOrchestratorGateway", "Port Interface", "Defines contracts for sync, async, and checkpoint operations")
    }

    Container_Boundary(infra_out_boundary, "Infrastructure Out (Adapters)") {
        Component(gatewayImpl, "AgentOrchestratorGatewayService", "RestClient Adapter", "Invocations to Python with MDC trace context & metrics")
        Component(metricsService, "AgentOpsMetricsService", "Micrometer Service", "Instruments Prometheus counters and timers")
        Component(repoProject, "MigrationProjectRepository", "JPA Repository", "CRUD on migration_projects")
        Component(repoTask, "AgentTaskRepository", "JPA Repository", "CRUD on agent_tasks")
        Component(repoAudit, "AuditLogRepository", "JPA Repository", "Append-only storage on agent_audit_logs")
    }

    Rel(controller, securityFilter, "Protected by")
    Rel(controller, usecase, "Invokes use case")
    Rel(usecase, gatewayPort, "Calls")
    Rel(gatewayPort, gatewayImpl, "Implemented by")
    Rel(gatewayImpl, dataMasker, "Sanitizes payloads via")
    Rel(gatewayImpl, metricsService, "Records token/time telemetry")
    Rel(gatewayImpl, repoTask, "Updates task state")
    Rel(gatewayImpl, repoAudit, "Appends cryptographic audit record")
```

---

## 3. Dynamic Sequence Workflows

### 3.1 Flow 1: Complete Migration with Human-in-the-Loop Sign-off

```mermaid
sequenceDiagram
    autonumber
    actor Arch as Lead Architect (Human)
    participant Core as Spring Boot Orchestrator
    participant DB as PostgreSQL 16
    participant Python as LangGraph Engine
    participant LLM as Claude 3.7 / Gemini

    Arch->>Core: POST /migrations/units/dispatch-async (PL/SQL snippet)
    Core->>Core: DataMaskingService.maskSensitiveData()
    Core->>DB: INSERT INTO agent_tasks (Status: DISPATCHED, TraceId)
    Core->>Python: POST /api/v1/graphs/legacy-migration/run (Payload + TraceId)
    
    Python->>Python: security_guardrail_node (Scan prompt injection)
    Python->>LLM: plsql_analyzer_node (Extract AST & Business Rules)
    LLM-->>Python: Return AST, referenced tables & complexity score (8.2)
    Python->>LLM: springboot_codegen_node (Synthesize Spring Boot 3 classes)
    LLM-->>Python: Return Entities, Repositories, Services
    Python->>LLM: qa_validator_node (Synthesize JUnit 5 tests)
    LLM-->>Python: Tests & Validation Score (0.95)
    
    Python->>Python: Conditional Edge: Complexity > 7.0 -> HALT before HITL Gatekeeper
    Python->>Python: Save checkpoint to memory/db (checkpoint_id: ckpt-9812)
    Python-->>Core: Response (requires_human_approval: true, checkpoint_id)
    
    Core->>DB: UPDATE agent_tasks SET status = 'AWAITING_HUMAN_APPROVAL'
    Core->>DB: INSERT INTO agent_audit_logs (Action: CHECKPOINT_PAUSED)
    
    Note over Arch, Core: Architect inspects generated Java code, tests, and AST diffs
    
    Arch->>Core: POST /migrations/tasks/{id}/approve (approved: true, notes)
    Core->>Python: POST /api/v1/graphs/legacy-migration/resume (thread_id, ckpt-9812, approved: true)
    Python->>Python: Update state (human_approved: true)
    Python->>Python: hitl_gatekeeper_node (Proceed to END)
    Python-->>Core: Response (status: APPROVED, final artifacts)
    
    Core->>DB: UPDATE agent_tasks SET status = 'APPROVED'
    Core->>DB: INSERT INTO agent_audit_logs (SHA-256 state_hash, Action: COMPLETED)
    Core-->>Arch: 200 OK (Migration approved and ready for PR merge)
```

---

### 3.2 Flow 2: Zero-Trust Defense against Prompt Injection & Jailbreak

```mermaid
sequenceDiagram
    autonumber
    actor Attacker as Rogue User / Embedded SQL Injection
    participant Core as Spring Boot Orchestrator
    participant Python as LangGraph Engine
    participant DB as PostgreSQL 16

    Attacker->>Core: POST /migrations/units/dispatch-async (PL/SQL containing "IGNORE ALL PREVIOUS INSTRUCTIONS")
    Core->>Python: POST /api/v1/graphs/legacy-migration/run
    Python->>Python: security_guardrail_node (Regex & heuristic scanning)
    
    Note over Python: Threat Detected: PROMPT_INJECTION_DETECTED<br/>Threat Level: CRITICAL
    
    Python->>Python: Neutralize payload -> [BLOCKED_BY_AGENT_SECURITY_GUARDRAILS]
    Python->>Python: Conditional Edge: is_safe == False -> ROUTE DIRECTLY TO END (Bypass LLM)
    Python-->>Core: 200 OK (status: BLOCKED, security_threats list)
    
    Core->>Core: AgentOpsMetricsService.recordSecurityViolation()
    Core->>DB: UPDATE agent_tasks SET status = 'FAILED', error = 'Security Violation'
    Core->>DB: INSERT INTO agent_audit_logs (Action: CRITICAL_SECURITY_ALERT, SHA-256)
    Core-->>Attacker: 400 Bad Request / Security Policy Violation
```

---

## 4. Threat Modeling (STRIDE Analysis)

| Threat Category | Potential Attack Vector | Mitigation Strategy in Platform |
| :--- | :--- | :--- |
| **Spoofing** | Attacker impersonates Lead Architect to approve low-quality or backdoored code. | Strict Spring Security filter verifying `ROLE_ARCHITECT` via cryptographic tokens / mTLS certificates. |
| **Tampering** | Code modified in-flight between agent output and Git pull request. | Cryptographic Non-Repudiation: Every step calculates and stores a SHA-256 hash in `agent_audit_logs.state_hash`. |
| **Repudiation** | An engineer denies approving a flawed financial calculation routine. | Immutable audit log in PostgreSQL storing architect ID, timestamp, approval notes, and state hash. |
| **Information Disclosure** | Legacy SQL contains production connection strings, PAN credit cards, or customer PII. | Pre-flight redaction via `DataMaskingService` replacing sensitive patterns before logging or LLM transmission. |
| **Denial of Service** | Infinite loops in agent reasoning; huge token consumption exhausting budgets. | Hard timeouts (180s in `RestClient`), token metering in `AgentOpsMetricsService`, and circuit-breaking. |
| **Elevation of Privilege** | Indirect Prompt Injection hijacking LLM to execute shell calls (`dbms_scheduler`, `xp_cmdshell`). | `security_guardrail_node` halts graph execution immediately upon detection before invoking Foundation Models. |

---

## 5. FinOps & Operational SLOs

- **Availability SLO:** 99.95% uptime for the Spring Boot Orchestrator and PostgreSQL persistence layer.
- **Latency Budget:**
  - Synchronous Gateway Timeout: max 180 seconds.
  - Asynchronous Batch Jobs: unbounded queue with backpressure handling (max queue: 200 tasks).
- **Cost Governance:**
  - Token threshold alert: Automated alerting triggered when a single task exceeds 15,000 total tokens or $0.15 USD in inference cost.
