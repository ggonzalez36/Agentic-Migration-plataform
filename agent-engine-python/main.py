from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel
from typing import Dict, Any, Optional
import uuid
from graph.migration_graph import compiled_migration_graph

app = FastAPI(
    title="AgentOps Legacy Migration Engine",
    version="1.0.0",
    description="Motor de Agentes LangGraph para modernización de código bancario y PL/SQL a Microservicios"
)

class RunWorkflowRequest(BaseModel):
    thread_id: str
    trace_id: str
    span_id: Optional[str] = None
    agent_type: str
    phase: str
    source_object_name: str
    legacy_code: str
    context: Optional[Dict[str, Any]] = {}

class ResumeCheckpointRequest(BaseModel):
    thread_id: str
    checkpoint_id: Optional[str] = None
    approved: bool
    architect_notes: Optional[str] = None
    manual_overrides: Optional[Dict[str, Any]] = {}

@app.post("/api/v1/graphs/legacy-migration/run")
async def run_migration_graph(
    payload: RunWorkflowRequest,
    authorization: Optional[str] = Header(None)
):
    thread_config = {"configurable": {"thread_id": payload.thread_id}}
    
    initial_state = {
        "thread_id": payload.thread_id,
        "trace_id": payload.trace_id,
        "source_object_name": payload.source_object_name,
        "legacy_plsql_code": payload.legacy_code,
        "context": payload.context or {},
        "business_rules": [],
        "qa_errors": [],
        "total_prompt_tokens": 0,
        "total_completion_tokens": 0,
        "estimated_cost_usd": 0.0,
        "requires_human_approval": False,
        "human_approved": None
    }
    
    try:
        # Ejecutar grafo hasta el final o hasta el punto de interrupción (HITL)
        final_state = compiled_migration_graph.invoke(initial_state, config=thread_config)
        
        # Inspeccionar si quedó pausado en checkpoint
        snapshot = compiled_migration_graph.get_state(thread_config)
        is_paused_at_hitl = len(snapshot.next) > 0 and "hitl_gatekeeper" in snapshot.next
        
        return {
            "thread_id": payload.thread_id,
            "run_id": str(uuid.uuid4()),
            "checkpoint_id": snapshot.config.get("configurable", {}).get("checkpoint_id", "ckpt-" + payload.thread_id),
            "generated_code": str(final_state.get("generated_spring_classes", {})),
            "analysis_summary": f"Extraídas {len(final_state.get('business_rules', []))} reglas de negocio.",
            "state_output": final_state,
            "prompt_tokens": final_state.get("total_prompt_tokens", 0),
            "completion_tokens": final_state.get("total_completion_tokens", 0),
            "total_tokens": final_state.get("total_prompt_tokens", 0) + final_state.get("total_completion_tokens", 0),
            "estimated_cost_usd": final_state.get("estimated_cost_usd", 0.0),
            "requires_human_approval": is_paused_at_hitl or final_state.get("requires_human_approval", False),
            "approval_message": "Aprobación requerida por regla financiera de criticidad alta." if is_paused_at_hitl else None
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/graphs/legacy-migration/resume")
async def resume_checkpoint(payload: ResumeCheckpointRequest):
    thread_config = {"configurable": {"thread_id": payload.thread_id}}
    
    try:
        # Actualizar estado con la decisión humana y reanudar grafo
        compiled_migration_graph.update_state(
            thread_config,
            {
                "human_approved": payload.approved,
                "architect_feedback": payload.architect_notes
            },
            as_node="hitl_gatekeeper"
        )
        
        resumed_state = compiled_migration_graph.invoke(None, config=thread_config)
        
        return {
            "thread_id": payload.thread_id,
            "run_id": str(uuid.uuid4()),
            "checkpoint_id": payload.checkpoint_id,
            "generated_code": str(resumed_state.get("generated_spring_classes", {})),
            "analysis_summary": "Reanudación tras revisión de arquitectura.",
            "state_output": resumed_state,
            "prompt_tokens": resumed_state.get("total_prompt_tokens", 0),
            "completion_tokens": resumed_state.get("total_completion_tokens", 0),
            "total_tokens": resumed_state.get("total_prompt_tokens", 0) + resumed_state.get("total_completion_tokens", 0),
            "estimated_cost_usd": resumed_state.get("estimated_cost_usd", 0.0),
            "requires_human_approval": False,
            "approval_message": None
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
