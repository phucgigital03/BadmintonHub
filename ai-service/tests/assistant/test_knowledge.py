"""RAG knowledge (Day 4): grounded + cited · below-threshold never fabricates · degrade on failure."""

from langchain_core.messages import HumanMessage

from app.assistant.knowledge import KnowledgeHit, KnowledgeService
from app.assistant.nodes import AssistantNodes, compose_knowledge_turn
from tests.assistant._fakes import FailingEmbedder, FakeEmbedder, FakeKnowledgeStore, fake_knowledge


def _state(text: str) -> dict:
    return {"messages": [HumanMessage(content=text)]}


def test_compose_grounded_answer_cites_source():
    hit = KnowledgeHit(
        content="Hủy trước hơn 24 giờ được hoàn 100% tổng tiền.",
        source="cancellation_policy.md",
        score=0.88,
    )
    turn = compose_knowledge_turn([hit])
    assert "hoàn 100%" in turn.content
    assert "Nguồn: cancellation_policy.md" in turn.content


def test_below_threshold_does_not_fabricate():
    hit = KnowledgeHit(content="không liên quan", source="x.md", score=0.1)
    turn = compose_knowledge_turn([hit])
    assert "chưa có thông tin" in turn.content.lower()
    assert "Gặp nhân viên" in turn.suggested_actions
    assert "x.md" not in turn.content  # never leaks an irrelevant chunk as if it were the answer


def test_no_hits_offers_escalate():
    turn = compose_knowledge_turn([])
    assert "Gặp nhân viên" in turn.suggested_actions


async def test_knowledge_node_returns_grounded_turn():
    hit = KnowledgeHit(content="Sân mở cửa 05:00–22:00.", source="club_info.md", score=0.9)
    nodes = AssistantNodes(None, knowledge=fake_knowledge([hit]))

    update = await nodes.knowledge(_state("câu lạc bộ mở cửa mấy giờ?"))

    assert "05:00" in update["turn"].content
    assert update["stage"] == "knowledge"


async def test_knowledge_node_degrades_on_embedder_failure():
    svc = KnowledgeService(FailingEmbedder(), FakeKnowledgeStore([]))
    nodes = AssistantNodes(None, knowledge=svc)

    update = await nodes.knowledge(_state("chính sách hủy?"))

    # embed rate-limit/outage → escalate offer, never a crash
    assert "Gặp nhân viên" in update["turn"].suggested_actions


async def test_service_embeds_the_query():
    embedder = FakeEmbedder()
    svc = KnowledgeService(embedder, FakeKnowledgeStore([]))
    await svc.search_knowledge("chính sách hủy sân?")
    assert embedder.queries == ["chính sách hủy sân?"]
