# ai-service (Python)

Conversational **booking concierge** for BadmintonHub — FastAPI · LangGraph · LLM provider-agnostic
(chat default = **Ollama** local `qwen2.5:3b`) · MCP. RAG embeddings still use Gemini.
Second capability of `ai-service` alongside the document-reconciliation feature. Port **3010**, registers with
Eureka as `ai-service` so the existing gateway route `lb://ai-service` (`/api/ai/**`) keeps working.

> This service is **Python, not Maven** — it is intentionally absent from the root `pom.xml <modules>`.
> Spec: `../usecase/UC_AI_Service_CustomerSupport.md`. Build plan: §16/§17.

> 🐍 **Mới với Python?** Đọc [`PYTHON_HUONG_DAN_DOC_CODE.md`](PYTHON_HUONG_DAN_DOC_CODE.md) — giải thích
> `__init__.py`, type hints, decorator, `async/await`, Pydantic/FastAPI/pytest… bằng chính code của service này,
> kèm bảng đối chiếu Java/Spring → Python.

## Day 1 scope (this commit)
Foundation + the 7 booking tools as a **FastMCP** server. No LLM/agent/graph yet (Day 2+).

- `app/main.py` — FastAPI app, `GET /health`, Eureka registration in lifespan, structlog + OTel→Zipkin.
- `app/config.py` — `pydantic-settings`, reads the **repo-root `.env`**.
- `app/security/` — HS256 JWT verify (shared `JWT_SECRET`) + request-scoped auth `ContextVar`.
- `app/db.py` + `app/models/` + `migrations/` — SQLAlchemy async (`ai_db`) + Alembic (3 foundation tables).
- `app/tools/` — 7 tools (thin httpx wrappers, forward the user JWT, no money logic) + `mcp_server.py`.

## Prerequisites
- Only [`uv`](https://docs.astral.sh/uv/). **No system Python needed** — this project is pinned to a
  uv-managed Python 3.12 (`[tool.uv] python-preference = "only-managed"`), so `uv sync` downloads and
  uses its own interpreter, independent of any Python on the machine.
- Repo-root `.env` with `JWT_SECRET`, `AI_DB_URL`, `GATEWAY_URL`, `EUREKA_URL`, `LLM_PROVIDER=ollama` +
  `OLLAMA_BASE_URL`/`OLLAMA_MODEL` (chat), and `GEMINI_API_KEY` (**RAG embeddings only**) — see `../.env.example`.
- [Ollama](https://ollama.com) running locally for the chat model: `ollama pull qwen2.5:3b` (one-time).
- `docker compose up -d postgres-ai` (ai_db, host port 5440).

## Commands
```bash
uv sync                       # install deps into .venv
uv run ruff check .           # lint
uv run pytest                 # unit tests (httpx mocked with respx)
uv run alembic upgrade head   # create the 3 tables in ai_db
uv run uvicorn app.main:app --host 0.0.0.0 --port 3010   # run (needs Eureka up to register)
curl http://localhost:3010/health        # → {"status":"UP"}
```

## Tools (§5 — all forward the user's Bearer, act-as-user)
| Tool | Endpoint | Type |
|---|---|---|
| `search_clubs` | `GET /api/clubs` | READ |
| `get_day_grid` | `GET /api/clubs/{id}/slots` | READ |
| `get_pricing` | `GET /api/clubs/{id}/pricing` | READ |
| `get_user_bookings` | `GET /api/bookings` | READ |
| `create_booking_hold` | `POST /api/bookings` | WRITE (human-confirm) |
| `initiate_payment` | `POST /api/payments/initiate` | WRITE (human-confirm) |
| `cancel_booking` | `POST /api/bookings/{id}/cancel` | WRITE (human-confirm) |

**Money-safety (§0.3):** tools only orchestrate already-hardened endpoints, never confirm money, no `/confirm`
path. WRITE tools run only after the agent's human-in-the-loop confirmation (Day 3).
