"""agent_run_log audit (§11.1) — every run writes one snapshot; PII is masked at write."""

from datetime import date

import httpx
import respx

from app.assistant.graph import build_graph, run_turn
from app.assistant.llm import model_label
from app.assistant.models import BookingIntent
from tests.assistant._fakes import FakeAuditSink, FakeModel
from tests.assistant.test_graph import _grid_window
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h

USER = "11111111-1111-1111-1111-111111111111"


@respx.mock
async def test_run_writes_full_audit_row_with_masked_phone():
    respx.get(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(200, json=h.booking_page())
    )
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    sink = FakeAuditSink()
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))

    await run_turn(
        graph,
        session_id="a1",
        bearer=TEST_BEARER,
        text="đặt pickleball 18-20h, liên hệ 0901234567",
        user_id=USER,
        sink=sink,
    )

    assert len(sink.rows) == 1
    row = sink.rows[0]

    # model + prompt version snapshot (reproducibility) — the active provider's label,
    # not a hardcoded one, so this holds under any LLM_PROVIDER (gemini / ollama / ...).
    assert row["model"] == model_label()
    assert row["prompt_version"] == "day6-hardened-v1"
    # outcome + latency
    assert row["decision"] == "proposed"
    assert isinstance(row["latency_ms"], int) and row["latency_ms"] >= 0
    # intent + proposal snapshots
    assert row["intent"]["sport"] == "PICKLEBALL"
    assert row["proposal"] is not None
    # tool calls & results captured (memory_load bookings + agent grid)
    assert isinstance(row["tool_calls"], list) and len(row["tool_calls"]) >= 1
    call = row["tool_calls"][0]
    assert "name" in call and "ok" in call and "latencyMs" in call

    # PII masked everywhere it could land
    assert row["proposal"]["customer_phone"] == "***"
    assert "0901234567" not in row["input_text"]  # phone in the utterance is masked
    dumped = str(row)
    assert "0901234567" not in dumped
