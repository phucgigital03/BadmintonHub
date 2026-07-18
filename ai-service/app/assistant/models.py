"""Agent data models (§9) + the LangGraph state (§4).

These are the assistant's *internal* structures — distinct from `app.tools.schemas`, which
mirror the Java service DTOs. `BookingIntent` is what `perceive` extracts; `CourtOption` /
`ProposedBooking` are what the CODE ranker produces; `AgentTurn` is what a turn returns to the
caller. `AgentState` is the graph's working memory (the "compressed memory" — we never stuff the
raw transcript into every LLM turn).
"""

from __future__ import annotations

from datetime import date as date_
from datetime import time
from decimal import Decimal
from typing import Annotated, Any, TypedDict
from uuid import UUID

from langgraph.graph.message import add_messages
from pydantic import BaseModel, Field

from app.tools.schemas import BookingItemInput

# --- §9 models -------------------------------------------------------------------


class BookingIntent(BaseModel):
    """What the user wants, extracted from natural-language Vietnamese (§3).

    On a follow-up edit ("đổi qua 19h") the perceive node MERGES the newly-mentioned fields
    over the prior intent — it does not re-parse from scratch — so statefulness is preserved.
    """

    date: date_ | None = None
    time_from: time | None = None
    time_to: time | None = None
    district: str | None = None
    sport: str | None = None
    budget_max: int | None = None  # VND
    duration_minutes: int | None = None
    party_size: int | None = None
    club_id: UUID | None = None
    missing: list[str] = Field(default_factory=list)

    def merge(self, update: BookingIntent) -> BookingIntent:
        """Overlay only the fields the newer message actually mentioned (non-None)."""
        base = self.model_dump()
        for key, value in update.model_dump().items():
            if key == "missing":
                continue
            if value is not None:
                base[key] = value
        merged = BookingIntent.model_validate(base)
        merged.missing = []  # recomputed by the gate
        return merged


class CourtOption(BaseModel):
    """A rankable, buildable option produced by the CODE ranker — grid-backed, never invented."""

    club_id: UUID
    court_id: UUID
    court_number: str
    sport: str | None = None
    slot_ids: list[UUID]
    start_time: time
    end_time: time
    total_price: Decimal
    within_budget: bool
    rationale: str = ""


class ProposedBooking(BaseModel):
    """The concrete hold the concierge would create — Day 2 only proposes it (no WRITE)."""

    club_id: UUID
    date: date_
    items: list[BookingItemInput]
    total_price: Decimal
    customer_name: str | None = None
    customer_phone: str | None = None
    hold_minutes: int = 10
    summary: str = ""


class ConfirmDecision(BaseModel):
    """Day 3 placeholder — the user's yes/no (+ edits) at the human_review interrupt."""

    confirmed: bool
    edits: str | None = None


class AgentTurn(BaseModel):
    """One assistant reply returned to the caller (later streamed over SSE)."""

    role: str = "assistant"
    content: str
    cards: list[dict[str, Any]] = Field(default_factory=list)
    suggested_actions: list[str] = Field(default_factory=list)


# --- §4 graph state --------------------------------------------------------------


class AgentState(TypedDict, total=False):
    session_id: str
    user_id: str  # from the JWT (never trust the client)
    jwt: str  # forwarded to tools (act-as-user)
    messages: Annotated[list, add_messages]
    intent: BookingIntent | None
    raw_slots: list  # AVAILABLE cells the agent gathered this turn (AvailableSlot[])
    candidates: list[CourtOption]
    proposal: ProposedBooking | None
    turn: AgentTurn | None  # the reply this turn produced
    stage: str  # perceive|gather|search|propose|await_confirm|held|payment|escalated|done
