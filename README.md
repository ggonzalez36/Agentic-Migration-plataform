# Agentic-Migration-plataform

Plataforma híbrida de modernización automatizada de sistemas legacy (PL/SQL a microservicios Spring Boot 3 con Java 21) respaldada por un motor agéntico en Python (LangGraph) y observabilidad de nivel bancario (AgentOps).

---

## 🏛️ Arquitectura General

La solución adopta una **Arquitectura Hexagonal (Ports & Adapters)** desacoplada:

1. **Backend Core & Orquestador (Spring Boot 3.3 / Java 21):**
   - Controlador REST robusto para ingesta de proyectos y despacho de unidades de migración.
   - Persistencia de metadatos del ciclo de vida y métricas de agentes (`MigrationProject`, `AgentTask`, `AuditLog`).
   - Gateway de comunicación síncrona/asíncrona vía `RestClient` con propagación de contexto distribuido W3C (`X-Trace-ID`, `X-Correlation-ID`).
   - Puntos de interrupción Human-in-the-Loop (HITL) para revisión y aprobación de arquitectura antes de merge.

2. **Capa de Inteligencia y Agentes (Python 3.11+ / LangGraph):**
   - **`plsql_analyzer`**: Extracción sintáctica y AST de esquemas PL/SQL, dependencias de tablas y lógica de negocio.
   - **`springboot_codegen`**: Síntesis de clases bajo patrones de Dominio (Entities, Repositories, Services, DTOs).
   - **`qa_validator`**: Generación de pruebas unitarias JUnit 5 y validación de cobertura de reglas.
   - **`hitl_gatekeeper`**: Interrupción determinista (`interrupt_before`) ante lógica de alta criticidad financiera.

3. **AgentOps & Compliance Bancario:**
   - Auditoría inmutable de cada invocación de nodo con hash SHA-256 para garantizar no-repudio.
   - Registro de consumo de tokens (prompt, completion, total), latencia de ejecución y coste estimado en USD.

---

## 📁 Estructura del Repositorio

```text
.
├── backend-orchestrator/            # Núcleo Spring Boot (Java 21)
│   ├── pom.xml
│   └── src/main/java/com/enterprise/agentops/
│       ├── domain/                  # Entidades JPA, Enums y Repositorios
│       ├── application/             # DTOs, Casos de uso y Puertos
│       └── infrastructure/          # Adaptador RestClient, REST API y Configuración
├── agent-engine-python/             # Motor Agéntico (FastAPI + LangGraph)
│   ├── main.py                      # Servidor FastAPI (/api/v1/graphs/legacy-migration)
│   └── graph/migration_graph.py     # Topología de Nodos y Checkpoints
├── contracts/                       # Esquemas JSON de comunicación
│   └── schemas/
└── README.md
```

---

## 🚀 Despliegue y Ejecución

### 1. Requisitos Previos
- Java 21 (JDK) & Maven 3.9+
- Python 3.11+
- PostgreSQL 15+

### 2. Motor de Agentes (Python / LangGraph)
```bash
cd agent-engine-python
pip install fastapi uvicorn langgraph langchain-core pydantic
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 3. Orquestador Spring Boot
```bash
cd backend-orchestrator
mvn clean install
mvn spring-boot:run
```

---

## 🔒 Estándares de Seguridad y Observabilidad
- Inyección de cabeceras de trazabilidad distribuida W3C / OpenTelemetry (`traceparent`, `X-Trace-ID`).
- Compatibilidad con esquemas de autenticación corporativos (OAuth2/OIDC, mTLS).
- Control transaccional idempotente en reanudación de checkpoints.
