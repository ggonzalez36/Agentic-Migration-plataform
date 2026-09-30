"""
Cybersecurity Guardrails for Generative AI & Multi-Agent Workflows
Defends against OWASP Top 10 for LLMs (LLM01: Prompt Injection, LLM06: Sensitive Information Disclosure)
"""

import re
from typing import Tuple, List
from dataclasses import dataclass

@dataclass
class GuardrailResult:
    is_safe: bool
    sanitized_text: str
    threat_level: str  # "NONE", "LOW", "CRITICAL"
    detected_threats: List[str]


class SecurityGuardrails:
    # Patrones de inyección de prompts y manipulación de instrucciones del sistema
    INJECTION_PATTERNS = [
        r"(?i)ignore\s+(all\s+)?(previous|prior)\s+(instructions|directives|prompts)",
        r"(?i)disregard\s+(the\s+)?above",
        r"(?i)you\s+are\s+now\s+(an?\s+)?unrestricted",
        r"(?i)system\s*override",
        r"(?i)do\s+anything\s+now",
        r"(?i)<\|im_start\|>",
        r"(?i)<\|system\|>",
        r"(?i)developer\s+mode\s+enabled",
        r"(?i)jailbreak",
        r"(?i)reveal\s+(your\s+)?(system\s+prompt|internal\s+rules)"
    ]

    # Patrones de código malicioso embebido en comentarios SQL/PLSQL
    SHELL_ESCAPE_PATTERNS = [
        r"(?i)dbms_scheduler\s*\.\s*create_program",
        r"(?i)exec\s+xp_cmdshell",
        r"(?i)/bin/(ba)?sh",
        r"(?i)cmd\.exe"
    ]

    @classmethod
    def scan_and_sanitize(cls, text: str) -> GuardrailResult:
        if not text:
            return GuardrailResult(is_safe=True, sanitized_text="", threat_level="NONE", detected_threats=[])

        detected = []

        # 1. Escanear inyección de prompts
        for pattern in cls.INJECTION_PATTERNS:
            if re.search(pattern, text):
                detected.append(f"PROMPT_INJECTION_DETECTED: {pattern}")

        # 2. Escanear comandos de escape o shell execution
        for pattern in cls.SHELL_ESCAPE_PATTERNS:
            if re.search(pattern, text):
                detected.append(f"MALICIOUS_SYSTEM_CALL_DETECTED: {pattern}")

        if detected:
            return GuardrailResult(
                is_safe=False,
                sanitized_text="[BLOCKED_BY_AGENT_SECURITY_GUARDRAILS]",
                threat_level="CRITICAL",
                detected_threats=detected
            )

        # 3. Sanitizar PII / PCI-DSS básica en el código fuente
        sanitized = re.sub(
            r"\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\b",
            "[REDACTED_PCI_CARD]",
            text
        )

        return GuardrailResult(
            is_safe=True,
            sanitized_text=sanitized,
            threat_level="NONE",
            detected_threats=[]
        )
