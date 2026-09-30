# ADR-001: Hybrid Orchestration Architecture (Spring Boot Core + Python LangGraph)

## Status
**Accepted** (March 2026)

## Context & Problem Statement
Enterprise legacy modernization (specifically migrating mission-critical Oracle PL/SQL packages containing complex business logic into modern Spring Boot microservices) requires:
1. Hardened transactional reliability, strict ACID persistence, role-based security, and regulatory compliance typical of tier-1 banking systems.
2. Advanced generative AI reasoning, cyclic state machines, and flexible multi-agent coordination capabilities.

Attempting to run pure Python for the entire enterprise system exposes the bank to risks in enterprise transaction management, enterprise concurrency, and strict auditing frameworks. Conversely, Java lacks native, battle-tested multi-agent graph ecosystems comparable to LangGraph.

## Decision
We adopt a **Decoupled Hybrid Architecture**:
- **System of Record & Orchestrator:** Spring Boot 3.3+ (Java 21) acts as the single source of truth, managing API access, security (RBAC/OIDC), persistence (PostgreSQL with JPA/Hibernate), distributed transactions, and the external audit ledger.
- **Agent Intelligence Engine:** Python 3.11+ running **LangGraph** exposed via FastAPI. It functions as an isolated computation worker executing state machine graphs, communicating with Foundation Models (Claude 3.7 / Gemini 1.5 Pro).
- **Communication Protocol:** RESTful JSON contracts over mTLS with mandatory W3C Trace Context propagation (`traceparent`, `X-Trace-ID`, `X-Correlation-ID`) and asymmetric signing.

## Consequences

### Positive
- **Clear Separation of Concerns:** Java manages corporate security, compliance, data integrity, and operational workflows; Python handles cognitive graph execution.
- **Fail-Safe Isolation:** An out-of-memory or timeout event in the LLM agent does not compromise database integrity or crash the orchestrator.
- **Interchangeable AI Engines:** The Python engine can be swapped, scaled independently as GPU/CPU worker pods on Kubernetes (KEDA), or updated without altering the core enterprise data model.

### Negative / Trade-offs
- **Network Boundary Overhead:** Adds an HTTP/mTLS network hop between the orchestrator and the agent engine (mitigated via keep-alive connection pooling with Spring 6 `RestClient`).
- **Polyglot Stack Maintenance:** Teams must maintain CI/CD pipelines for both Java/Maven and Python/Poetry.
