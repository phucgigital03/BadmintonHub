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
from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel

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

    @field_validator("time_from", "time_to")
    @classmethod
    def _strip_tzinfo(cls, v: time | None) -> time | None:
        """The whole platform works in naive local time (vi_parse, the grid, the ranker all
        use naive times). The LLM's structured output can emit a tz-aware time (pydantic
        attaches a pydantic_core.TzInfo) — that both breaks the checkpointer's msgpack
        serializer AND would make naive-vs-aware ranker comparisons raise. Normalize here at
        the model boundary so times are always naive regardless of source."""
        return v.replace(tzinfo=None) if v is not None and v.tzinfo is not None else v

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
    """The user's yes/no (+ edits) at the human_review interrupt (§9).

    customer_name/customer_phone are optional overrides: the FE sends them when the
    guardrail bounced asking for contact info (UserResponse has no phone — §11.4).
    """

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")

    confirmed: bool
    edits: str | None = None
    customer_name: str | None = None
    customer_phone: str | None = None


class AgentTurn(BaseModel):
    """One assistant reply returned to the caller (streamed over SSE as camelCase JSON)."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

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
    default_contact: dict | None  # {"name","phone"} from the most recent booking (§11.4)
    hold: dict | None  # BookingResponse dump — the real PENDING hold (totalPrice authoritative)
    payment: dict | None  # PaymentResponse dump — Bank-QR info for the FE
    turn: AgentTurn | None  # the reply this turn produced
    stage: str  # perceive|gather|search|propose|await_confirm|held|payment|escalated|done
