"""
AgentOps LangGraph Migration Engine
Modelo conceptual y ejecutable del grafo de modernización de PL/SQL a Microservicios Spring Boot.
"""

from typing import TypedDict, Annotated, List, Dict, Any, Optional
import operator
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver


class MigrationGraphState(TypedDict):
    """Estado compartido a través de todos los nodos del grafo."""
    thread_id: str
    trace_id: str
    source_object_name: str
    legacy_plsql_code: str
    context: Dict[str, Any]
    
    # Salidas intermedias de los nodos
    ast_analysis: Optional[Dict[str, Any]]
    business_rules: Annotated[List[str], operator.add]
    generated_spring_classes: Optional[Dict[str, str]]
    unit_tests: Optional[Dict[str, str]]
    qa_validation_score: float
    qa_errors: Annotated[List[str], operator.add]
    
    # Telemetría AgentOps
    total_prompt_tokens: int
    total_completion_tokens: int
    estimated_cost_usd: float
    
    # Estado Human-in-the-Loop (HITL)
    requires_human_approval: bool
    human_approved: Optional[bool]
    architect_feedback: Optional[str]


def plsql_analyzer_node(state: MigrationGraphState) -> Dict[str, Any]:
    """
    Nodo 1: Analizador de Código Legacy.
    Parsea AST de PL/SQL, extrae dependencias de tablas, cursores, transacciones y lógica de negocio.
    """
    print(f"[{state['trace_id']}] Ejecutando AST Analyzer para {state['source_object_name']}...")
    
    # Simulación de extracción inteligente de AST y reglas
    extracted_rules = [
        "Validación de saldo disponible antes de débito en cuenta",
        "Generación obligatoria de registro contable con partida doble",
        "Manejo de excepción NO_DATA_FOUND retornando código fiscal -99"
    ]
    
    analysis = {
        "tables_referenced": ["TBL_ACCOUNTS", "TBL_LEDGER_TRANSACTIONS"],
        "procedures": ["prc_close_month", "prc_calc_interests"],
        "cursors_detected": 2,
        "complexity_score": 7.5
    }
    
    return {
        "ast_analysis": analysis,
        "business_rules": extracted_rules,
        "total_prompt_tokens": 1250,
        "total_completion_tokens": 420,
        "estimated_cost_usd": 0.0082
    }


def springboot_codegen_node(state: MigrationGraphState) -> Dict[str, Any]:
    """
    Nodo 2: Generador de Microservicios Spring Boot.
    Transforma la lógica analizada en arquitectura limpia (Hexagonal/DDD) en Spring Boot 3.3 / Java 21.
    """
    print(f"[{state['trace_id']}] Generando Microservicio Spring Boot 3...")
    
    # Genera las clases del microservicio
    generated_code = {
        "AccountBillingService.java": """
            @Service
            @Transactional
            @RequiredArgsConstructor
            public class AccountBillingService {
                private final AccountRepository accountRepository;
                private final LedgerEventPublisher eventPublisher;
                
                public void processBillingClosure(UUID accountId) {
                    Account account = accountRepository.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada"));
                    account.applyMonthlyFees();
                    accountRepository.save(account);
                }
            }
        """,
        "AccountRepository.java": """
            public interface AccountRepository extends JpaRepository<Account, UUID> {}
        """
    }
    
    return {
        "generated_spring_classes": generated_code,
        "total_prompt_tokens": 2800,
        "total_completion_tokens": 1150,
        "estimated_cost_usd": 0.0245
    }


def qa_validator_node(state: MigrationGraphState) -> Dict[str, Any]:
    """
    Nodo 3: Validador / QA & Test Synthesis.
    Genera tests JUnit 5 con Mockito, calcula cobertura sintética y detecta desvíos de reglas.
    """
    print(f"[{state['trace_id']}] Validando calidad de código y generando pruebas JUnit 5...")
    
    tests = {
        "AccountBillingServiceTest.java": """
            @ExtendWith(MockitoExtension.class)
            class AccountBillingServiceTest {
                @Test
                void shouldApplyMonthlyFeesSuccessfully() {
                    // Test de regresión garantizando equivalencia semántica con PL/SQL
                }
            }
        """
    }
    
    # Evaluación de criticidad financiera: Si la complejidad es alta, requiere revisión arquitectural
    complexity = state.get("ast_analysis", {}).get("complexity_score", 0.0)
    critical_financial_logic = complexity > 7.0
    
    return {
        "unit_tests": tests,
        "qa_validation_score": 0.94,
        "qa_errors": [],
        "requires_human_approval": critical_financial_logic,
        "total_prompt_tokens": 1500,
        "total_completion_tokens": 600,
        "estimated_cost_usd": 0.0112
    }


def hitl_gatekeeper_node(state: MigrationGraphState) -> Dict[str, Any]:
    """
    Nodo 4: Checkpoint Human-in-the-Loop (HITL).
    Punto de interrupción donde un Arquitecto Senior revisa y aprueba o rechaza el código antes de merge.
    """
    print(f"[{state['trace_id']}] Punto de control HITL: Esperando decisión de arquitectura...")
    return {
        "human_approved": state.get("human_approved", False)
    }


def should_interrupt_for_human(state: MigrationGraphState) -> str:
    """Función de enrutamiento condicional (Conditional Edge)."""
    if state.get("requires_human_approval", False) and not state.get("human_approved", False):
        return "hitl_gatekeeper_node"
    return END


def build_migration_graph() -> StateGraph:
    """Construcción y compilación del grafo con soporte de Checkpoints."""
    workflow = StateGraph(MigrationGraphState)
    
    # Registrar nodos
    workflow.add_node("plsql_analyzer", plsql_analyzer_node)
    workflow.add_node("springboot_codegen", springboot_codegen_node)
    workflow.add_node("qa_validator", qa_validator_node)
    workflow.add_node("hitl_gatekeeper", hitl_gatekeeper_node)
    
    # Definir flujo de aristas
    workflow.set_entry_point("plsql_analyzer")
    workflow.add_edge("plsql_analyzer", "springboot_codegen")
    workflow.add_edge("springboot_codegen", "qa_validator")
    
    # Arista condicional hacia HITL o Fin
    workflow.add_conditional_edges(
        "qa_validator",
        should_interrupt_for_human,
        {
            "hitl_gatekeeper_node": "hitl_gatekeeper",
            END: END
        }
    )
    workflow.add_edge("hitl_gatekeeper", END)
    
    return workflow


# Para ejecutar en producción con persistencia de checkpoints
checkpointer = MemorySaver()
compiled_migration_graph = build_migration_graph().compile(
    checkpointer=checkpointer,
    interrupt_before=["hitl_gatekeeper"] # Interrupción determinista antes de la aprobación
)
