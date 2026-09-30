# ADR-003: Human-in-the-Loop (HITL) Checkpoints & Four-Eyes Governance

## Status
**Accepted** (March 2026)

## Context & Problem Statement
In core banking and insurance systems, legacy PL/SQL routines frequently encode high-risk financial business logic:
- Ledger account balancing and currency rounding conventions.
- Regulatory tax withholdings and anti-money laundering (AML) checks.
- Direct database mutation locks (`SELECT ... FOR UPDATE`).

Allowing an LLM-based agent to autonomously migrate and commit such logic into production without deterministic architectural validation violates the **Four-Eyes Principle** mandated by financial supervisory authorities (EBA, OCC, FINRA).

## Decision
We mandate a **Deterministic Human-in-the-Loop (HITL) Checkpoint Architecture**:
1. **Dynamic Risk Classification**:
   - The `qa_validator` and `plsql_analyzer` nodes calculate an automated **Risk & Complexity Index (RCI)** based on cyclomatic complexity, financial table mutations, and transaction rollback patterns.
   - Any unit with RCI > 7.0 or mutating designated critical ledger entities automatically enters an `INTERRUPT` state.
2. **LangGraph Checkpointing with MemorySaver / PostgresSaver**:
   - The graph halts execution before the `hitl_gatekeeper` node (`interrupt_before=["hitl_gatekeeper"]`).
   - The execution state snapshot is frozen in the database with a unique `checkpoint_id` and `thread_id`.
3. **Enterprise Sign-off Portal**:
   - The task status transitions to `AWAITING_HUMAN_APPROVAL` in Spring Boot.
   - A Senior Solutions/Enterprise Architect must review the proposed transformation, inspection diffs, and synthesized JUnit test results.
   - The architect signs off via `POST /migrations/tasks/{taskId}/approve` with approval status and optional manual code overrides.
   - Only upon authorized sign-off is the LangGraph thread resumed.

## Consequences

### Positive
- Strict compliance with banking governance and four-eyes operational controls.
- Eliminates the risk of catastrophic silent logic degradation in high-value financial calculations.
- Provides architects with fine-grained control and manual override intervention points.

### Negative / Trade-offs
- Migration processes for complex units become asynchronous and dependent on human turnaround times.
- Requires long-term state persistence for paused threads in the orchestrator.
