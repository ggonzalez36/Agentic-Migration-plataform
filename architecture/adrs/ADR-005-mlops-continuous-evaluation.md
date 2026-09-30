# ADR-005: MLOps / LLMOps Continuous Evaluation & Regression Gates

## Status
**Accepted** (March 2026)

## Context & Problem Statement
Unlike deterministic compiler software, generative AI multi-agent pipelines are subject to:
1. **Model Drift & Non-Determinism:** Foundation model API updates or temperature settings causing regressions in code syntax or logic equivalence.
2. **Prompt Regression:** Altering prompt instructions in one node (e.g., AST analyzer) inadvertently breaking downstream expectations in the Spring Boot code generator.
3. **FinOps & Cost Inflation:** Unmonitored prompt expansions triggering exponential token consumption and exceeding corporate cloud budgets.
4. **Adversarial Vulnerability Resurgence:** Modifications to input processing weakening prompt injection defenses.

In a Tier-1 financial setting, code generators cannot be promoted to production environments without passing automated continuous evaluation (CE) quality and cost gates.

## Decision
We establish an **Automated MLOps / LLMOps Continuous Evaluation Pipeline** integrated into GitHub Actions:

1. **Golden Dataset Versioning (`golden_dataset.json`):**
   - Maintained under version control alongside agent source code.
   - Contains vetted reference migration scenarios across various complexity tiers (high-risk financial settlement, audit triggers, and adversarial prompt injection attack vectors).

2. **Automated Quality & Regression Gating (`eval_benchmark.py`):**
   - Executed on every pull request targeting the agent codebase (`agent-engine-python/**`).
   - Gates enforced:
     - **Security Gate:** Adversarial injection cases MUST be intercepted with 100% precision.
     - **AST Semantic Gate:** Extracted business rules and entity relationships must match baseline expectations.
     - **HITL Routing Gate:** Complex units must deterministically route to the `hitl_gatekeeper` node.
     - **FinOps Gate:** Token consumption must remain strictly below per-unit budget thresholds (e.g., < 6,000 tokens per unit).

3. **Continuous Reporting & PR Feedback:**
   - Detailed metrics (latency percentiles, token usage, USD cost, and pass rate) are formatted as Markdown and published directly to `$GITHUB_STEP_SUMMARY` and preserved as workflow artifacts.

## Consequences

### Positive
- Prevents silent regressions in code generation fidelity and security guardrails before merge.
- Enforces predictable financial budgeting (FinOps) on multi-agent inference costs.
- Provides compliance auditors with verifiable benchmark history for every deployed agent release.

### Negative / Trade-offs
- PR validation times increase slightly to run the evaluation suite.
- Golden datasets must be periodically curated and expanded to reflect emerging business scenarios.
