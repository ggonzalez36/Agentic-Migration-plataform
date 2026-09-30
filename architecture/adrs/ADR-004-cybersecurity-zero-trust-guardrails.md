# ADR-004: Zero-Trust Cybersecurity, LLM Guardrails, and Data Protection

## Status
**Accepted** (March 2026)

## Context & Problem Statement
Integrating generative AI into core financial infrastructures exposes the system to attack vectors specific to LLMs (OWASP Top 10 for LLMs 2025/2026):
1. **Prompt Injection (Direct & Indirect):** Malicious instructions embedded in legacy SQL comments or metadata attempting to hijack agent generation instructions.
2. **Sensitive Information Disclosure (PII / PCI-DSS):** Legacy procedures containing hardcoded credentials, test credit cards, national identification numbers, or confidential database connection strings leaking into LLM inference logs.
3. **Insecure Output Handling:** Hallucinated shell commands, insecure SQL queries, or remote code execution risks in generated microservices.
4. **Unauthorized Access:** Lack of fine-grained role control over architectural decisions.

## Decision
We enforce a comprehensive **Defense-in-Depth Cybersecurity Framework**:

1. **Spring Security Zero-Trust & RBAC:**
   - Stateless authentication enforced via API Key or OIDC JWT Bearer tokens.
   - Granular authorities: `ROLE_ARCHITECT` (can approve HITL checkpoints and trigger migrations), `ROLE_OPERATOR` (can dispatch jobs and view progress), and `ROLE_AUDITOR` (read-only access to immutable audit traces).
   - Rate limiting via sliding token buckets to prevent denial-of-service and API flooding.

2. **In-Flight Data Sanitization & PII Masking (`DataMaskingService`):**
   - Before any legacy code or payload is persisted into `agent_audit_logs` or dispatched over the wire, regex-based sanitizers redact:
     - Primary Account Numbers (PAN / PCI-DSS Credit Cards): Replaced with `[REDACTED_CARD]`.
     - Tax IDs / SSNs / National IDs: Replaced with `[REDACTED_SSN]`.
     - Database connection strings, API tokens, and private keys: Replaced with `[REDACTED_SECRET]`.

3. **Multi-Stage LLM Guardrails (`SecurityGuardrailNode`):**
   - The first node executed in Python LangGraph performs prompt injection scanning using heuristic anomaly detection and pattern matching against system instruction tampering delimiters (`"IGNORE PREVIOUS INSTRUCTIONS"`, `"<system>"`, etc.).
   - If a prompt injection attempt is detected, the workflow immediately terminates, flagging a `CRITICAL_SECURITY_ALERT` in the audit log.

4. **Static Application Security Testing (SAST) in CodeGen:**
   - The generated Spring Boot code is statically scanned for insecure practices (e.g., SQL string concatenation preventing SQLi, hardcoded secrets, weak cryptographic algorithms).

## Consequences

### Positive
- Fully compliant with PCI-DSS 4.0, GDPR Article 32, and OWASP LLM Top 10 guidelines.
- Prevents data exfiltration and intellectual property leaks to third-party inference providers.
- Protects the generated codebase from supply chain or injection vulnerabilities.

### Negative / Trade-offs
- Slight latency increase (~50-100ms) for preprocessing redaction and prompt scanning.
- Maintenance overhead of regex and sanitization dictionaries as new threat patterns emerge.
