import httpx
import pytest

from app.main import create_app


@pytest.mark.asyncio
async def test_health_returns_up():
    app = create_app()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "UP"}
