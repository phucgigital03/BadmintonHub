"""Offline fakes — no network/DB. Injected via build_graph(model=..., prefs_store=..., knowledge=...)."""

from __future__ import annotations

from langchain_core.messages import AIMessage

from app.assistant.knowledge import KnowledgeHit, KnowledgeService
from app.assistant.models import BookingIntent
from app.assistant.preferences import PreferenceSnapshot


class _FakeStructured:
    def __init__(self, intent: BookingIntent):
        self._intent = intent

    async def ainvoke(self, messages):
        return self._intent


class _FakeBound:
    def __init__(self, responses):
        self._responses = list(responses)
        self._i = 0

    async def ainvoke(self, messages):
        r = self._responses[min(self._i, len(self._responses) - 1)]
        self._i += 1
        return r


class FakeModel:
    """with_structured_output → a canned BookingIntent; bind_tools → scripted AIMessages."""

    def __init__(self, intent: BookingIntent, tool_responses=None):
        self._intent = intent
        self._tool_responses = tool_responses or [AIMessage(content="")]

    def with_structured_output(self, schema):
        return _FakeStructured(self._intent)

    def bind_tools(self, tools):
        return _FakeBound(self._tool_responses)


# --- Day 4 fakes (embeddings · knowledge · preferences) --------------------------------


class FakeEmbedder:
    """Deterministic offline embedder — records calls; the vector is a placeholder because the
    FakeKnowledgeStore returns preset hits regardless of it."""

    def __init__(self, dim: int = 8):
        self.dim = dim
        self.queries: list[str] = []

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[float(len(t) % 7)] * self.dim for t in texts]

    async def embed_query(self, text: str) -> list[float]:
        self.queries.append(text)
        return [float(len(text) % 7)] * self.dim


class FailingEmbedder(FakeEmbedder):
    """Simulates an embedding rate-limit / outage → knowledge path must degrade, not crash."""

    async def embed_query(self, text: str) -> list[float]:
        raise RuntimeError("RESOURCE_EXHAUSTED (simulated)")


class FakeKnowledgeStore:
    """Returns preset hits (with preset scores) so grounding/threshold behaviour is controllable."""

    def __init__(self, hits: list[KnowledgeHit]):
        self._hits = hits

    async def search(self, query_vector, top_k: int) -> list[KnowledgeHit]:
        return self._hits[:top_k]


def fake_knowledge(hits: list[KnowledgeHit], embedder: FakeEmbedder | None = None) -> KnowledgeService:
    return KnowledgeService(embedder or FakeEmbedder(), FakeKnowledgeStore(hits), top_k=4)


class FakePreferenceStore:
    """In-memory L2 store — no DB. Records upserts."""

    def __init__(self, initial: dict[str, PreferenceSnapshot] | None = None):
        self._data: dict[str, PreferenceSnapshot] = dict(initial or {})

    async def load(self, user_id: str) -> PreferenceSnapshot | None:
        return self._data.get(user_id)

    async def upsert(self, user_id: str, snapshot: PreferenceSnapshot) -> None:
        self._data[user_id] = snapshot


# --- Day 6 fakes (rate limiter · audit sink) -------------------------------------------


class FakeRateLimiter:
    """Deterministic limiter — `allowed` controls the verdict; records the users it saw."""

    def __init__(self, allowed: bool = True):
        self.allowed = allowed
        self.calls: list[str] = []

    async def allow(self, user_id: str) -> bool:
        self.calls.append(user_id)
        return self.allowed


class FakeAuditSink:
    """Captures the agent_run_log rows in memory so a test can assert them (no DB)."""

    def __init__(self):
        self.rows: list[dict] = []

    async def write(self, row: dict) -> None:
        self.rows.append(row)


class FakeRedis:
    """Minimal async redis double for RedisRateLimiter — counts incr; optionally raises."""

    def __init__(self, *, raises: bool = False):
        self._counts: dict[str, int] = {}
        self._raises = raises

    async def incr(self, key: str) -> int:
        if self._raises:
            raise RuntimeError("redis down (simulated)")
        self._counts[key] = self._counts.get(key, 0) + 1
        return self._counts[key]

    async def expire(self, key: str, seconds: int) -> bool:
        return True
