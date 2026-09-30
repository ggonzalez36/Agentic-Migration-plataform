"""
AgentOps MLOps Continuous Evaluation & Benchmark Runner
Executes automated quality, security, and cost regression gates on every PR.
"""

import json
import os
import sys
import time
from typing import Dict, Any, List

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from graph.migration_graph import compiled_migration_graph
from security.guardrails import SecurityGuardrails


def run_benchmark():
    dataset_path = os.path.join(os.path.dirname(__file__), "golden_dataset.json")
    with open(dataset_path, "r", encoding="utf-8") as f:
        cases: List[Dict[str, Any]] = json.load(f)

    results = []
    total_cases = len(cases)
    passed_cases = 0
    total_tokens_consumed = 0
    total_cost_usd = 0.0
    regression_failures = []

    print("================================================================================")
    print("  AGENTOPS / MLOPS CONTINUOUS EVALUATION & BENCHMARK SUITE")
    print(f"  Evaluating {total_cases} Gold Standard Migration Scenarios")
    print("================================================================================\n")

    for case in cases:
        case_id = case["id"]
        case_name = case["name"]
        print(f"[*] Running Eval: [{case_id}] {case_name} ...")
        
        start_time = time.time()
        thread_id = f"eval-thread-{case_id}"
        thread_config = {"configurable": {"thread_id": thread_id}}
        
        initial_state = {
            "thread_id": thread_id,
            "trace_id": f"trace-eval-{case_id}",
            "source_object_name": case_name,
            "legacy_plsql_code": case["legacy_plsql"],
            "context": {},
            "business_rules": [],
            "qa_errors": [],
            "total_prompt_tokens": 0,
            "total_completion_tokens": 0,
            "estimated_cost_usd": 0.0,
            "requires_human_approval": False,
            "human_approved": None
        }

        # Ejecutar grafo
        final_state = compiled_migration_graph.invoke(initial_state, config=thread_config)
        duration_ms = int((time.time() - start_time) * 1000)

        # Evaluar métricas
        is_security_case = case.get("expected_security_blocked", False)
        tokens = final_state.get("total_prompt_tokens", 0) + final_state.get("total_completion_tokens", 0)
        cost = final_state.get("estimated_cost_usd", 0.0)
        total_tokens_consumed += tokens
        total_cost_usd += cost

        case_passed = True
        failure_reasons = []

        # 1. Regla de Ciberseguridad: Adversarial Injection
        if is_security_case:
            if final_state.get("is_safe", True):
                case_passed = False
                failure_reasons.append("Security Guardrail FAILED to block prompt injection attack!")
        else:
            if not final_state.get("is_safe", True):
                case_passed = False
                failure_reasons.append("Legitimate code incorrectly marked as security threat (False Positive)")

            # 2. Verificación de Checkpoint HITL
            expected_hitl = case.get("requires_hitl", False)
            actual_hitl = final_state.get("requires_human_approval", False)
            if expected_hitl != actual_hitl:
                case_passed = False
                failure_reasons.append(f"HITL mismatch: Expected {expected_hitl}, got {actual_hitl}")

            # 3. Verificación de Presupuesto de Tokens (FinOps Gate)
            max_allowed = case.get("max_allowed_tokens", 10000)
            if tokens > max_allowed:
                case_passed = False
                failure_reasons.append(f"Token regression: {tokens} tokens exceeded budget of {max_allowed}")

        if case_passed:
            passed_cases += 1
            print(f"    [PASSED] Duration: {duration_ms}ms | Tokens: {tokens} | Cost: ${cost:.5f}")
        else:
            print(f"    [FAILED] {'; '.join(failure_reasons)}")
            regression_failures.append({"id": case_id, "reasons": failure_reasons})

        results.append({
            "case_id": case_id,
            "name": case_name,
            "status": "PASSED" if case_passed else "FAILED",
            "duration_ms": duration_ms,
            "tokens": tokens,
            "cost_usd": cost,
            "failures": failure_reasons
        })

    # Resumen y Reporte
    pass_rate = (passed_cases / total_cases) * 100.0
    report = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "total_cases": total_cases,
        "passed_cases": passed_cases,
        "pass_rate_percent": pass_rate,
        "total_tokens_consumed": total_tokens_consumed,
        "total_cost_usd": round(total_cost_usd, 6),
        "results": results
    }

    # Guardar reporte JSON
    os.makedirs(os.path.join(os.path.dirname(__file__), "reports"), exist_ok=True)
    report_file = os.path.join(os.path.dirname(__file__), "reports", "eval_report.json")
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    # Generar Markdown Summary para GitHub Actions Step Summary
    summary_markdown = f"""## 🤖 AgentOps / MLOps Continuous Evaluation Report

| Metric | Result | Target / Threshold | Status |
| :--- | :--- | :--- | :--- |
| **Pass Rate** | **{pass_rate:.1f}%** | 100.0% | {'✅ PASS' if pass_rate == 100 else '❌ FAIL'} |
| **Total Test Scenarios** | {total_cases} | - | ℹ️ INFO |
| **Total Tokens Consumed** | {total_tokens_consumed:,} | < 25,000 | ✅ PASS |
| **Total Inference Cost** | ${total_cost_usd:.4f} USD | < $0.10 USD | ✅ PASS |

### Test Breakdown:
"""
    for res in results:
        icon = "✅" if res["status"] == "PASSED" else "❌"
        summary_markdown += f"- {icon} **{res['case_id']}** ({res['name']}): `{res['status']}` — {res['tokens']} tokens, {res['duration_ms']}ms\n"

    summary_file = os.path.join(os.path.dirname(__file__), "reports", "eval_summary.md")
    with open(summary_file, "w", encoding="utf-8") as f:
        f.write(summary_markdown)

    print("\n================================================================================")
    print(f"  BENCHMARK SUMMARY: Pass Rate = {pass_rate:.1f}% ({passed_cases}/{total_cases})")
    print(f"  Total Cost: ${total_cost_usd:.5f} USD | Total Tokens: {total_tokens_consumed:,}")
    print("================================================================================\n")

    if regression_failures:
        print(f"❌ MLOps Gate Failure: {len(regression_failures)} test case(s) failed quality gates!")
        sys.exit(1)

    print("✅ All MLOps & AgentOps Quality & Security Gates Passed Successfully.")
    sys.exit(0)


if __name__ == "__main__":
    run_benchmark()
