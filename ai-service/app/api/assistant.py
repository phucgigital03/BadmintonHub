"""Assistant endpoints (§9) — REST + SSE, mounted under /api/ai/assistant (gateway `lb://`).

POST /sessions · POST /{id}/messages (SSE) · POST /{id}/confirm · POST /{id}/escalate ·
GET /{id}. /confirm resumes the graph paused at the human_review interrupt; it can only ever
lead to create_booking_hold + initiate_payment — confirming MONEY stays with STAFF (§0.3).
/escalate is FE-driven: it returns a summary only and never calls chat-service (§11.5).
"""

from __future__ import annotations

import json
from typing import Annotated, Any

import structlog
from fastapi import APIRouter, Depends, HTTPException, Request
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from app.assistant import sessions
from app.assistant.graph import (
    answer_knowledge_while_paused,
    get_default_graph,
    get_thread_state,
    has_pending_interrupt,
    resume_confirm,
    snapshot_has_interrupt,
)
from app.assistant.models import ConfirmDecision
from app.assistant.nodes import classify_route
from app.security.deps import error_body, require_user
from app.tools import schemas

log = structlog.get_logger(__name__)

router = APIRouter(prefix="/api/ai/assistant", tags=["assistant"])


def get_graph():
    """Dependency so tests can override with a FakeModel-backed graph."""
    return get_default_graph()


def get_knowledge(request: Request):
    """Shared KnowledgeService installed by the app lifespan (None when unset — e.g. tests)."""
    return getattr(request.app.state, "knowledge", None)


# FastAPI-idiomatic Annotated dependencies (also keeps ruff B008 happy)
UserClaims = Annotated[dict, Depends(require_user)]
GraphDep = Annotated[Any, Depends(get_graph)]
KnowledgeDep = Annotated[Any, Depends(get_knowledge)]


class MessageRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)


def _bearer(request: Request) -> str:
    return request.headers.get("Authorization", "").removeprefix("Bearer ").strip()


def _resolve(session_id: str, claims: dict) -> None:
    try:
        sessions.resolve(session_id, claims.get("sub", ""))
    except sessions.SessionNotFound as exc:
        raise HTTPException(
            404, detail=error_body("SESSION_NOT_FOUND", "Phiên không tồn tại")
        ) from exc
    except sessions.SessionExpired as exc:
        raise HTTPException(
            410, detail=error_body("SESSION_EXPIRED", "Phiên đã hết hạn — hãy mở phiên mới")
        ) from exc


def _turn_payload(values: dict, awaiting: bool) -> dict:
    turn = values.get("turn")
    return {
        "turn": turn.model_dump(mode="json", by_alias=True) if turn else None,
        "stage": values.get("stage") or "new",
        "awaitingConfirm": awaiting,
    }


def _camel(model_cls, data: dict | None) -> dict | None:
    """State stores snake_case dumps; the API answers in the platform's camelCase."""
    if not data:
        return None
    return model_cls.model_validate(data).model_dump(mode="json", by_alias=True)


# --- endpoints ---------------------------------------------------------------------


@router.post("/sessions")
async def create_session(claims: UserClaims) -> dict:
    return {"sessionId": sessions.create(claims.get("sub", ""))}


@router.post("/{session_id}/messages")
async def send_message(
    session_id: str,
    body: MessageRequest,
    request: Request,
    claims: UserClaims,
    graph: GraphDep,
    knowledge: KnowledgeDep,
):
    _resolve(session_id, claims)
    bearer = _bearer(request)
    config = {"configurable": {"thread_id": session_id}}

    async def gen():
        # the streaming body runs after the endpoint returns — re-set the auth context here
        from app.security.context import set_auth

        set_auth(bearer, claims)
        try:
            if await has_pending_interrupt(graph, session_id):
                snapshot = await get_thread_state(graph, session_id)
                stage = snapshot.values.get("stage")
                intent = snapshot.values.get("intent")
                # mixed-intent: a knowledge question while a proposal is pending → answer inline
                # WITHOUT resuming, so the interrupt stays and /confirm keeps working (§ Day 4)
                is_knowledge = classify_route(intent, stage, body.text) == "knowledge"
                if knowledge is not None and is_knowledge:
                    values = await answer_knowledge_while_paused(snapshot, knowledge, body.text)
                    yield {"event": "node", "data": json.dumps({"node": "knowledge"})}
                    payload = _turn_payload(values, awaiting=True)
                    yield {"event": "turn", "data": json.dumps(payload, ensure_ascii=False)}
                    yield {"event": "done", "data": "{}"}
                    return
                # otherwise free text is a non-confirm edit ("đổi qua 19h")
                resume = ConfirmDecision(confirmed=False, edits=body.text)
                stream_input: object = Command(resume=resume.model_dump())
            else:
                stream_input = {
                    "messages": [HumanMessage(content=body.text)],
                    "session_id": session_id,
                    "user_id": claims.get("sub", ""),
                    "jwt": bearer,
                }
            async for chunk in graph.astream(stream_input, config, stream_mode="updates"):
                for node in chunk:
                    if node != "__interrupt__":
                        yield {"event": "node", "data": json.dumps({"node": node})}
            snapshot = await get_thread_state(graph, session_id)
            payload = _turn_payload(snapshot.values, snapshot_has_interrupt(snapshot))
            yield {"event": "turn", "data": json.dumps(payload, ensure_ascii=False)}
            yield {"event": "done", "data": "{}"}
        except Exception as exc:  # noqa: BLE001 — stream already started; report, don't crash
            log.error("assistant.stream_error", session_id=session_id, error=str(exc))
            yield {
                "event": "error",
                "data": json.dumps({"code": "AGENT_ERROR", "message": str(exc)}),
            }

    return EventSourceResponse(gen())


@router.post("/{session_id}/confirm")
async def confirm(
    session_id: str,
    decision: ConfirmDecision,
    request: Request,
    claims: UserClaims,
    graph: GraphDep,
) -> dict:
    _resolve(session_id, claims)
    if not await has_pending_interrupt(graph, session_id):
        raise HTTPException(
            409,
            detail=error_body("NO_PENDING_PROPOSAL", "Không có đề xuất nào đang chờ xác nhận"),
        )
    result = await resume_confirm(
        graph, session_id=session_id, bearer=_bearer(request), decision=decision, claims=claims
    )
    awaiting = await has_pending_interrupt(graph, session_id)
    payload = _turn_payload(result, awaiting)
    # success = THIS resume ended on the payment node (stage check guards against stale
    # hold/payment left in state by an earlier booking in the same session)
    if not awaiting and result.get("stage") == "payment" and result.get("payment"):
        payload["booking"] = _camel(schemas.BookingResponse, result.get("hold"))
        payload["payment"] = _camel(schemas.PaymentResponse, result.get("payment"))
    return payload


@router.post("/{session_id}/escalate")
async def escalate(
    session_id: str,
    claims: UserClaims,
    graph: GraphDep,
) -> dict:
    _resolve(session_id, claims)
    snapshot = await get_thread_state(graph, session_id)
    summary = _build_summary(snapshot.values)
    if snapshot.values:
        try:
            await graph.aupdate_state(
                {"configurable": {"thread_id": session_id}}, {"stage": "escalated"}
            )
        except Exception as exc:  # noqa: BLE001 — stage marking is best-effort bookkeeping
            log.warning("escalate.update_state_failed", error=str(exc))
    return {"summary": summary}


@router.get("/{session_id}")
async def get_session(
    session_id: str,
    claims: UserClaims,
    graph: GraphDep,
) -> dict:
    _resolve(session_id, claims)
    snapshot = await get_thread_state(graph, session_id)
    values = snapshot.values
    intent = values.get("intent")
    proposal = values.get("proposal")
    return {
        "sessionId": session_id,
        "transcript": _transcript(values.get("messages", [])),
        "intent": intent.model_dump(mode="json") if intent else None,
        "proposal": proposal.model_dump(mode="json") if proposal else None,
        "stage": values.get("stage") or "new",
        "awaitingConfirm": snapshot_has_interrupt(snapshot),
    }


# --- helpers -----------------------------------------------------------------------


def _transcript(messages: list) -> list[dict]:
    out: list[dict] = []
    for msg in messages:
        content = msg.content if isinstance(msg.content, str) else ""
        if not content:
            continue
        if isinstance(msg, HumanMessage):
            out.append({"role": "user", "content": content})
        elif isinstance(msg, AIMessage):
            out.append({"role": "assistant", "content": content})
    return out


def _build_summary(values: dict) -> str:
    """Deterministic hand-off summary (§11.5) — the FE posts this as the first STAFF message."""
    parts = ["Khách chuyển từ trợ lý AI đặt sân sang hỗ trợ viên."]
    intent = values.get("intent")
    if intent is not None:
        parts.append(
            "Nhu cầu: "
            f"môn {intent.sport or '?'} · ngày {intent.date or '?'} · "
            f"khung {intent.time_from or '?'}–{intent.time_to or '?'} · "
            f"ngân sách {f'{intent.budget_max:,}đ' if intent.budget_max else 'không nêu'}."
        )
    proposal = values.get("proposal")
    if proposal is not None and proposal.summary:
        parts.append(f"Đề xuất gần nhất: {proposal.summary}")
    hold = values.get("hold")
    if hold:
        parts.append(f"Đang có đơn giữ chỗ: {hold.get('id')} (trạng thái {hold.get('status')}).")
    parts.append(f"Trạng thái phiên: {values.get('stage') or 'mới mở'}.")
    return " ".join(parts)
