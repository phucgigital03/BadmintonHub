"""Offline fake chat model — no network. Injected via build_graph(model=FakeModel(...))."""

from __future__ import annotations

from langchain_core.messages import AIMessage

from app.assistant.models import BookingIntent


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
