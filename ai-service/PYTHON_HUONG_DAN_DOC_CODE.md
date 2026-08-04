# Python cho người mới — đọc hiểu `ai-service`

> Tài liệu **học**, không phải tài liệu build. Mục tiêu: đọc xong thì mở được **bất kỳ file `.py` nào**
> trong `ai-service/` và hiểu nó đang làm gì — kể cả những thứ chưa từng gặp khi học Python cơ bản
> (`__init__.py`, `async def`, `@decorator`, `str | None`, `Protocol`, `yield`…).
>
> **Mọi ví dụ trong tài liệu này là code THẬT của repo**, kèm đường dẫn dạng `app/config.py:89`
> (số sau dấu `:` là số dòng — mở file, nhảy tới đúng dòng đó mà đối chiếu).
>
> Tài liệu anh em: `README.md` (cách chạy) · `../usecase/UC_AI_Service_CustomerSupport.md` §18 (vì sao thiết kế
> kiến trúc AI như vậy). Tài liệu này lấp phần còn thiếu: **ngôn ngữ Python + thư viện**.

---

## Mục lục

**Phần A — Định hướng**
1. [Cách dùng tài liệu + lộ trình đọc code](#1-cách-dùng-tài-liệu--lộ-trình-đọc-code)
2. [Bảng mỏ neo: Java/Spring → Python](#2-bảng-mỏ-neo-javaspring--python)

**Phần B — Nền tảng ngôn ngữ**
3. [Bộ khung một project Python](#3-bộ-khung-một-project-python)
4. [Module · package · `__init__.py` · import](#4-module--package--__init__py--import)
5. [Cú pháp nền](#5-cú-pháp-nền)
6. [Type hints](#6-type-hints)
7. [Hàm](#7-hàm)
8. [Class](#8-class)
9. [Decorator — dấu `@`](#9-decorator--dấu-)
10. [async / await](#10-async--await)
11. [Context manager & `yield`](#11-context-manager--yield)
12. [Exception & triết lý "degrade, đừng chết"](#12-exception--triết-lý-degrade-đừng-chết)
13. [Dữ liệu & vòng lặp](#13-dữ-liệu--vòng-lặp)
14. [State cấp module: `global`, cache, ContextVar](#14-state-cấp-module-global-cache-contextvar)

**Phần C — Thư viện**

15. [Thấy dòng này thì hiểu là gì](#15-thấy-dòng-này-thì-hiểu-là-gì)

**Phần D — Thực hành**

16. [3 đường đi xuyên code](#16-3-đường-đi-xuyên-code)
17. [Bảng tra ký hiệu lạ](#17-bảng-tra-ký-hiệu-lạ)
18. [Tự học tiếp + 8 bài tập](#18-tự-học-tiếp--8-bài-tập)

---

## 1. Cách dùng tài liệu + lộ trình đọc code

**Cách dùng**: mở tài liệu này ở một nửa màn hình, IDE ở nửa kia. Mỗi khi thấy ref `app/config.py:89`
thì nhảy sang IDE mở đúng dòng đó. Đọc tài liệu mà không mở code song song thì **hiệu quả giảm quá nửa**.

**Lộ trình đọc code — từ dễ tới khó.** Đừng mở `nodes.py` (688 dòng) trước, sẽ nản.

| # | File | Dòng | Nội dung | Học được gì |
|---|---|---|---|---|
| 1 | `app/config.py` | 91 | Toàn bộ cấu hình service | class, type hint, decorator `@lru_cache` |
| 2 | `app/db.py` | 48 | Kết nối DB | biến module, `global`, `yield` |
| 3 | `app/security/context.py` | 33 | Nơi cất JWT của request | ContextVar, hàm đơn giản |
| 4 | `app/tools/schemas.py` | 163 | Khuôn dữ liệu JSON | Pydantic, generic `Page[T]` |
| 5 | `app/tools/http_client.py` | 78 | Gọi HTTP ra ngoài | `async with`, `try/finally`, Exception tự định nghĩa |
| 6 | `app/tools/booking_tools.py` | 129 | 7 "tool" của AI | `async def`, comprehension, ráp mọi thứ ở trên |
| 7 | `app/main.py` | 165 | Điểm khởi động service | FastAPI, lifespan, `@asynccontextmanager` |
| 8 | `app/api/assistant.py` | 351 | 5 endpoint REST/SSE | router, `Depends`, `Annotated` |
| 9 | `app/assistant/models.py` | 142 | Cấu trúc dữ liệu của agent | `TypedDict`, `field_validator` |
| 10 | `app/assistant/nodes.py` | 688 | Bộ não agent | tổng hợp tất cả |

> **Quy tắc sống còn khi đọc code lạ**: đọc **docstring đầu file trước** (đoạn `"""..."""` ngay dòng 1).
> Trong repo này *mọi* file đều có, và nó nói thẳng file đó tồn tại để làm gì. Đọc 5 dòng đó tiết kiệm
> 30 phút mò mẫm.

---

## 2. Bảng mỏ neo: Java/Spring → Python

Bạn đã rành Spring Boot (6 service Java trong repo này). Gần như mọi khái niệm ở `ai-service` đều có
một "anh em song sinh" bên Java. Đây là bảng dịch:

| Java / Spring bạn đã biết | Tương đương ở `ai-service` | File thật |
|---|---|---|
| `pom.xml` (deps + plugin config) | `pyproject.toml` (deps + config ruff/pytest) | `pyproject.toml` |
| Maven — `mvn clean install` | uv — `uv sync` | `uv.lock` |
| `~/.m2/repository` + `target/` | `.venv/` (thư viện nằm ngay trong project) | — |
| `@SpringBootApplication` + Tomcat nhúng | `FastAPI()` + `uvicorn` | `app/main.py:141` |
| `@RestController` + `@PostMapping` | `APIRouter()` + `@router.post` | `app/api/assistant.py:41,112` |
| DTO + Jackson + `@Valid` | Pydantic `BaseModel` + `Field` | `app/tools/schemas.py:20` |
| `application.yml` + `@ConfigurationProperties` | `pydantic-settings` class `Settings` | `app/config.py:14` |
| JPA / Hibernate entity | SQLAlchemy model | `app/models/user_preference.py:16` |
| Flyway migration | Alembic (`migrations/versions/`) | `migrations/versions/0001_initial.py` |
| `@Autowired` (Dependency Injection) | `Depends(...)` | `app/api/assistant.py:65` |
| `interface` + `implements` | `Protocol` (**không cần** `implements`) | `app/assistant/knowledge.py:27` |
| `ThreadLocal<T>` | `ContextVar` | `app/security/context.py:13` |
| JUnit + Mockito | pytest + class fake tự viết | `tests/assistant/_fakes.py:31` |
| Checkstyle | ruff | `pyproject.toml:65` |
| SLF4J `log.info("text " + x)` | structlog `log.info("event", key=x)` | `app/main.py:31` |
| `RestTemplate` / `WebClient` | `httpx.AsyncClient` | `app/tools/http_client.py:59` |
| `@Transactional`, `@Scheduled`… | decorator nói chung (`@...`) | §9 |
| `CompletableFuture` / WebFlux | `async` / `await` | §10 |

**Ba khác biệt lớn nhất phải nhớ ngay:**

1. **Không có `{ }`** — Python dùng **thụt lề** để biết khối lệnh bắt đầu/kết thúc. Sai thụt lề = sai logic.
2. **Type hint KHÔNG ép kiểu lúc chạy.** Viết `def f(x: int)` rồi truyền `"abc"` vào, Python **vẫn chạy**
   (chỉ IDE/linter cảnh báo). Khác hẳn Java. Muốn kiểm thật thì phải nhờ Pydantic (§15).
3. **Không có `private`/`public`.** Chỉ có **quy ước**: tên bắt đầu bằng `_` nghĩa là "nội bộ, đừng đụng"
   — ví dụ `_engine`, `_bad()`, `_FakeBound`. Không ai chặn bạn cả, chỉ là văn hoá.

---

## 3. Bộ khung một project Python

```
ai-service/
├── pyproject.toml      ← "pom.xml" — khai báo thư viện + cấu hình ruff/pytest
├── uv.lock             ← khoá phiên bản chính xác (như pom.xml.lock) — commit vào git
├── .python-version     ← ghi "3.12" — phiên bản Python của project
├── .venv/              ← nơi thư viện được cài (gitignore) — như thư mục target/
├── app/                ← source code
├── tests/              ← test
├── migrations/         ← Alembic (như Flyway)
├── knowledge/          ← dữ liệu (4 file .md cho RAG)
└── scripts/            ← script chạy tay
```

### `pyproject.toml` — 1 file thay cho 4 file cấu hình

Java cần `pom.xml` + `checkstyle.xml` + `surefire config`… Python gom hết vào 1 file:

```toml
[project]                       # ai-service/pyproject.toml:1
name = "ai-service"
requires-python = ">=3.12"
dependencies = [                # ← thư viện chạy thật (như <dependencies> của Maven)
    "fastapi>=0.115",
    "httpx>=0.27",
    ...
]

[dependency-groups]
dev = ["pytest>=8.3", "ruff>=0.8", ...]   # ← chỉ dùng khi dev/test (như <scope>test</scope>)

[tool.pytest.ini_options]       # ai-service/pyproject.toml:57 — cấu hình pytest
[tool.ruff]                     # ai-service/pyproject.toml:65 — cấu hình linter
```

Đọc `[tool.xxx]` là "cấu hình cho công cụ tên xxx".

### `.venv/` — virtual environment

Java cài thư viện vào `~/.m2` **dùng chung toàn máy**. Python thì ngược lại: mỗi project có một
**môi trường ảo riêng** ở `.venv/`, chứa cả trình thông dịch Python lẫn thư viện. Nhờ vậy project A dùng
`httpx 0.27`, project B dùng `httpx 0.20` mà không đánh nhau.

### `uv` — công cụ quản lý (mới, thay cho pip/venv/poetry)

```bash
uv sync                 # đọc pyproject.toml + uv.lock → cài đủ thư viện vào .venv  (≈ mvn install)
uv run pytest           # chạy lệnh BÊN TRONG .venv (không cần "activate" thủ công)
uv run ruff check .     # lint                                                       (≈ checkstyle)
uv run uvicorn app.main:app --port 3010    # chạy service                            (≈ spring-boot:run)
```

> **Luôn gõ `uv run <lệnh>`**. Gõ thẳng `pytest` sẽ dùng Python của hệ điều hành → thiếu thư viện → lỗi khó hiểu.

Hai dòng đáng chú ý trong `pyproject.toml:49-55`:

```toml
[tool.uv]
package = false                     # đây là ứng dụng, không phải thư viện để người khác cài
python-preference = "only-managed"  # CHỈ dùng Python do uv tự tải về
python-downloads = "automatic"
```

`only-managed` nghĩa là: `ai-service` xài một bản Python 3.12 **riêng** do uv tải, cất ở
`~/.local/share/uv/python/`. Bạn xoá/nâng cấp Python của máy → service này **không hề hấn gì**
(chỉ cần `uv sync` lại). Đây là lý do trong máy bạn đang có nhiều bản Python cùng lúc mà không sao.

---

## 4. Module · package · `__init__.py` · import

**Đây là câu hỏi bạn hỏi. Trả lời đầy đủ ở đây.**

### 4.1 Ba khái niệm

| Khái niệm | Là gì | Ví dụ |
|---|---|---|
| **module** | 1 file `.py` | `app/config.py` là module `app.config` |
| **package** | 1 thư mục **có file `__init__.py`** | thư mục `app/` là package `app` |
| **sub-package** | package lồng trong package | `app/tools/` là `app.tools` |

Đường dẫn thư mục ↔ đường dẫn import, dấu `/` đổi thành dấu `.`:

```
app/tools/schemas.py     →     from app.tools import schemas
                               from app.tools.schemas import SlotResponse
```

### 4.2 Vậy `__init__.py` để làm gì?

**Một câu**: nó nói với Python *"thư mục này là một package, hãy cho phép import từ nó"*, và nó là
**đoạn code chạy đầu tiên** khi package được import lần đầu.

> Ghi chú chính xác: từ Python 3.3, thiếu `__init__.py` vẫn import được (cơ chế "namespace package"),
> nhưng **mọi project thực tế đều giữ nó** vì rõ ràng hơn và tránh những lỗi rất khó chịu với
> test-discovery/tooling. Repo này giữ đủ.

Trong `ai-service` có **đúng 3 kiểu** `__init__.py`, và cả 3 đều có lý do:

**Kiểu 1 — file rỗng (0 dòng)**: `app/api/__init__.py`, `app/security/__init__.py`, `tests/__init__.py`

Chỉ để đánh dấu "đây là package". Không có gì bên trong.
Nhờ `tests/__init__.py` mà file test viết được `from tests.conftest import TEST_BEARER`
(`tests/tools/test_read_tools.py:10`).

**Kiểu 2 — chỉ có docstring**: `app/__init__.py` (1 dòng)

```python
"""BadmintonHub ai-service — Python (FastAPI · LangGraph · MCP)."""
```

Đánh dấu package + mô tả. Không hơn.

**Kiểu 3 — re-export (quan trọng nhất)**: `app/assistant/__init__.py`

```python
# ai-service/app/assistant/__init__.py:8
from app.assistant.graph import build_graph, run_turn
from app.assistant.models import AgentState, AgentTurn, BookingIntent, CourtOption, ProposedBooking

__all__ = [
    "AgentState",
    "AgentTurn",
    ...
]
```

Nó **gom** các tên quan trọng nằm rải rác trong package ra ngoài "mặt tiền", để người dùng viết ngắn gọn:

```python
from app.assistant import BookingIntent        # ngắn — nhờ re-export
from app.assistant.models import BookingIntent # dài — vẫn đúng
```

Giống việc bạn để một `index.java` gom lại các class hay dùng. `__all__` = danh sách "tên công khai
của package này" (ảnh hưởng tới `from app.assistant import *` và là tài liệu cho người đọc).

Một ca **re-export vì lý do kỹ thuật** — `app/models/__init__.py`:

```python
"""SQLAlchemy models — imported here so Alembic sees them on Base.metadata."""
from app.models.agent_run_log import AgentRunLog
from app.models.assistant_message import AssistantMessage
from app.models.kb_chunk import KbChunk
from app.models.user_preference import UserPreference
```

Ở đây import **không phải cho gọn**, mà vì: Alembic (công cụ migration) chỉ "nhìn thấy" một bảng khi
class model đã được **import ít nhất một lần**. Import 4 dòng này trong `__init__.py` bảo đảm chỉ cần
`import app.models` là cả 4 bảng đã đăng ký. Đây đúng nghĩa "code chạy khi package được import".

### 4.3 `__pycache__/` là gì?

Thư mục Python tự sinh, chứa file `.pyc` = bản đã biên dịch sẵn (bytecode) để lần import sau nhanh hơn.
**Không bao giờ sửa, không commit** — đã có trong `.gitignore` cùng `.venv/`, `.pytest_cache/`, `.ruff_cache/`.

### 4.4 Import tuyệt đối vs import trễ

Repo này dùng **import tuyệt đối** (luôn bắt đầu bằng `app.` hoặc `tests.`), đặt ở **đầu file**:

```python
# ai-service/app/tools/booking_tools.py:12
from app.tools import schemas, validate
from app.tools.http_client import request
```

Nhưng thỉnh thoảng bạn sẽ thấy `import` nằm **bên trong một hàm** — trông "sai quy tắc" nhưng là cố ý:

```python
# ai-service/app/tools/http_client.py:31
"""... Lazy import keeps the HTTP layer decoupled ..."""
try:
    from app.assistant.audit import note_tool_call     # ← import TRỄ, bên trong hàm
```

Hai lý do dùng import trễ:

1. **Tránh vòng lặp import (circular import)**: `http_client` cần `audit`, mà `audit` (gián tiếp) lại cần
   `http_client` → nếu cả hai import nhau ở đầu file, Python sẽ lỗi. Đẩy một chiều vào trong hàm là hết.
2. **Không tải thứ nặng lúc khởi động**: `app/assistant/rate_limit.py:49` chỉ `import redis.asyncio`
   khi thật sự cần → chạy test không cần cài/khởi động Redis.

### 4.5 Vì sao `import app...` chạy được?

Python tìm module theo một danh sách thư mục. Với pytest, dòng này lo việc đó:

```toml
# ai-service/pyproject.toml:60
pythonpath = ["."]      # "." = thư mục ai-service/ → nên thấy được package app/ và tests/
```

Còn khi chạy service, bạn đứng ở `ai-service/` gõ `uv run uvicorn app.main:app` — thư mục hiện tại
đã nằm trong đường tìm kiếm.

> `app.main:app` đọc là: **module** `app.main`, lấy **biến** tên `app` trong đó
> (chính là `app = create_app()` ở `app/main.py:165`).

---

## 5. Cú pháp nền

### 5.1 Thụt lề thay `{ }`

```python
# ai-service/app/tools/validate.py:32
def sport(value: str | None, *, required: bool) -> str | None:
    if value is None or value == "":
        if required:
            raise _bad("Thiếu môn thể thao (PICKLEBALL hoặc BADMINTON).")
        return None
    normalized = value.strip().upper()
```

Dấu `:` cuối dòng mở một khối; các dòng thụt vào (chuẩn: **4 dấu cách**) thuộc khối đó. Hết thụt lề là hết khối.
Không có `;` cuối dòng, không có `{ }`.

### 5.2 Docstring vs comment

```python
def sport(...):
    """Normalize + enum-check a sport. None allowed only when not required."""   # ← DOCSTRING
    # None allowed only when not required                                        # ← comment
```

- **Docstring** = chuỗi `"""..."""` đặt **ngay dòng đầu** của file / hàm / class. Nó là **một phần của
  chương trình** (đọc được lúc chạy qua `__doc__`, IDE hiện khi rê chuột). ≈ Javadoc.
- **Comment** = bắt đầu bằng `#`, Python bỏ qua hoàn toàn.

Repo này **mọi file đều mở đầu bằng docstring giải thích file đó tồn tại để làm gì** — hãy đọc nó trước tiên.

### 5.3 f-string

Dấu `f` trước chuỗi cho phép nhúng biểu thức trong `{ }`:

```python
# ai-service/app/assistant/rate_limit.py:29
key = f"rate_limit:ai:{user_id}"        # → "rate_limit:ai:abc-123"

# ai-service/app/tools/http_client.py:54
headers = {"Authorization": f"Bearer {get_bearer()}"}   # gọi được cả hàm bên trong
```

≈ `String.format` / text block của Java nhưng gọn hơn nhiều.

### 5.4 Giá trị đặc biệt & quy ước tên

| Python | Java |
|---|---|
| `None` | `null` |
| `True` / `False` | `true` / `false` |
| `dict` | `Map` |
| `list` | `List` |
| `set` (`{"PICKLEBALL", "BADMINTON"}` — `validate.py:21`) | `Set` |
| `tuple` (bộ bất biến) | — |

| Quy ước tên | Ý nghĩa | Ví dụ trong repo |
|---|---|---|
| `snake_case` | hàm & biến (không phải camelCase!) | `get_day_grid`, `total_price` |
| `PascalCase` | class | `BookingIntent`, `ToolError` |
| `UPPER_CASE` | hằng số | `MAX_ITEMS`, `SPORTS`, `RECENT_WINDOW` |
| `_ten` | **nội bộ** — đừng dùng từ ngoài | `_engine`, `_bad()`, `_strip_accents()` |
| `__ten__` | "dunder" — tên đặc biệt Python định nghĩa sẵn | `__init__`, `__name__`, `__all__` |

---

## 6. Type hints

Bạn sẽ thấy chú thích kiểu ở **gần như mọi dòng** của repo này. Đọc được chúng là đọc được 50% code.

### 6.1 Cú pháp cơ bản

```python
# ai-service/app/tools/booking_tools.py:43
async def get_day_grid(
    club_id: UUID, date: date_, sport: str | None = None
) -> list[schemas.AvailableSlot]:
```

Dịch sang tiếng Việt: hàm `get_day_grid` nhận `club_id` kiểu `UUID`, `date` kiểu `date`,
`sport` kiểu **chuỗi hoặc None** (mặc định `None`); **trả về** (`->`) một **danh sách** `AvailableSlot`.

| Ký hiệu | Nghĩa | Java tương đương |
|---|---|---|
| `x: int` | tham số/biến kiểu int | `int x` |
| `-> str` | kiểu trả về | `String f()` |
| `str \| None` | chuỗi **hoặc** None | `@Nullable String` |
| `list[str]` | danh sách chuỗi | `List<String>` |
| `dict[str, Any]` | map khoá chuỗi, giá trị bất kỳ | `Map<String, Object>` |
| `Any` | "kiểu gì cũng được" | `Object` |

> **Nhắc lại lần nữa vì rất quan trọng**: type hint **không được kiểm tra lúc chạy**. Nó phục vụ
> người đọc + IDE + linter. Muốn *thật sự* validate dữ liệu vào (JSON từ ngoài, output của LLM) thì phải
> dùng Pydantic — xem §15.

### 6.2 `from __future__ import annotations`

Xuất hiện ở dòng ~7-10 của gần như mọi file:

```python
# ai-service/app/tools/http_client.py:8
from __future__ import annotations
```

Tác dụng: Python **không đánh giá** các chú thích kiểu lúc chạy (giữ chúng dưới dạng chuỗi). Lợi ích:
import nhanh hơn, và **được phép dùng tên class chưa định nghĩa** — đúng ca này:

```python
# ai-service/app/assistant/models.py:57
def merge(self, update: BookingIntent) -> BookingIntent:
```

`BookingIntent` đang được định nghĩa dở mà đã tự nhắc tên mình. Không có dòng `__future__` đó thì lỗi.

### 6.3 Generic — `Page[T]`

```python
# ai-service/app/tools/schemas.py:28
class Page[T](CamelModel):
    """Spring Data Page<T> JSON shape."""
    content: list[T] = []
    total_elements: int = 0
```

Đây là cú pháp generic mới của Python 3.12, tương ứng `class Page<T>` bên Java. Dùng:

```python
# ai-service/app/tools/booking_tools.py:40
return schemas.Page[schemas.ClubResponse].model_validate(resp.json())   # ≈ Page<ClubResponse>
```

### 6.4 `TypedDict` — dict nhưng biết trước có khoá nào

```python
# ai-service/app/assistant/models.py:127
class AgentState(TypedDict, total=False):
    session_id: str
    user_id: str
    intent: BookingIntent | None
    proposal: ProposedBooking | None
    stage: str
```

`AgentState` **vẫn là một `dict` bình thường** lúc chạy (`state["stage"]`, `state.get("turn")`), nhưng IDE
biết nó có những khoá nào và kiểu gì. `total=False` = **mọi khoá đều không bắt buộc**.

Đây là "bộ nhớ làm việc" của agent, được truyền qua từng bước xử lý.

### 6.5 `Protocol` — interface kiểu Python

Java: muốn có interface thì `interface X` rồi class phải `implements X`.
Python: khai báo `Protocol`, và **bất kỳ class nào có đủ method đúng chữ ký đều tự động hợp lệ** —
không cần khai báo kế thừa gì cả (gọi là *structural typing* hay "duck typing").

```python
# ai-service/app/assistant/knowledge.py:27
class KnowledgeStore(Protocol):
    async def search(self, query_vector: list[float], top_k: int) -> list[KnowledgeHit]: ...
```

(Dấu `...` là `Ellipsis` — chỗ này nghĩa "không có thân hàm", giống `;` sau method của interface Java.)

Hai class thoả protocol này mà **không** class nào viết `implements`:

- `PgvectorKnowledgeStore` (`knowledge.py:31`) — tìm thật trong Postgres.
- `FakeKnowledgeStore` (trong `tests/assistant/_fakes.py`) — trả dữ liệu dựng sẵn, không cần DB.

Nhờ vậy **161 test của service chạy được offline**: test bơm hàng fake vào, code thật không biết và không cần biết.
Cùng khuôn mẫu ở `Embedder` (`embeddings.py:15`), `RateLimiter` (`rate_limit.py:18`), `AuditSink`, `PreferenceStore`.

### 6.6 `Annotated`

"Kiểu + thông tin đính kèm". FastAPI dùng để biết cách cung cấp tham số:

```python
# ai-service/app/api/assistant.py:65
UserClaims = Annotated[dict, Depends(require_user)]
```

Đọc là: "kiểu `dict`, và giá trị được lấy bằng cách gọi `require_user`". Xem tiếp §15 (FastAPI).

---

## 7. Hàm

### 7.1 Tham số mặc định

```python
# ai-service/app/tools/booking_tools.py:18
async def search_clubs(
    district: str | None = None,
    sport: str | None = None,
    ...
)
```

Gọi được cả 3 kiểu — Java phải viết 3 overload:

```python
await search_clubs()
await search_clubs(sport="PICKLEBALL")               # gọi theo TÊN, bỏ qua district
await search_clubs("Quận 3", "PICKLEBALL")           # gọi theo vị trí
```

> ⚠️ Bẫy kinh điển: **đừng để mặc định là list/dict** (`def f(x=[])`) — nó bị dùng chung giữa các lần gọi.
> Repo này né đúng cách bằng `Field(default_factory=list)` (`models.py:45`).

### 7.2 Dấu `*` — ép gọi theo tên

```python
# ai-service/app/tools/http_client.py:46
async def request(
    method: str,
    path: str,
    *,                              # ← mọi tham số SAU dấu này BẮT BUỘC gọi theo tên
    params: dict[str, Any] | None = None,
    json: Any | None = None,
) -> httpx.Response:
```

```python
await request("GET", "/api/clubs", params=params)   # ✅
await request("GET", "/api/clubs", params)          # ❌ TypeError
```

Mục đích: chỗ gọi luôn đọc được rõ nghĩa, và sau này thêm tham số không sợ vỡ thứ tự.
Xem thêm `rate_limit.py:23`, `graph.py:38`.

### 7.3 Hàm là một giá trị

Hàm truyền được như biến — nền tảng của decorator (§9) và của LangGraph:

```python
# ai-service/app/assistant/graph.py:45
sg.add_node("route", nodes.route)        # truyền chính HÀM route vào, không gọi nó
```

Chú ý: `nodes.route` (không ngoặc) = *bản thân hàm*; `nodes.route()` = *gọi hàm*.

Chuyện này từng gây bug thật trong repo — xem comment cảnh báo tại `nodes.py:167-168`: nếu class có **thuộc tính
trùng tên với method** (`self.knowledge` và `def knowledge`), thì `nodes.knowledge` sẽ trả về thuộc tính chứ không
phải method, và graph nhận nhầm đối tượng thay vì hàm. Đó là lý do thuộc tính được đổi tên thành `self.knowledge_svc`.

---

## 8. Class

### 8.1 `__init__` (khác `__init__.py`!)

Hai thứ **hoàn toàn khác nhau**, chỉ trùng tên:

- `__init__.py` = **file** đánh dấu package (§4).
- `__init__` = **constructor** của class.

```python
# ai-service/app/assistant/knowledge.py:54
class KnowledgeService:
    def __init__(self, embedder: Embedder, store: KnowledgeStore, top_k: int | None = None):
        self._embedder = embedder
        self._store = store
        self._top_k = top_k or get_settings().rag_top_k

    async def search_knowledge(self, query: str) -> list[KnowledgeHit]:
        vector = await self._embedder.embed_query(query)
        return await self._store.search(vector, self._top_k)
```

Java tương ứng:

```java
class KnowledgeService {
    private final Embedder embedder;
    KnowledgeService(Embedder embedder, ...) { this.embedder = embedder; }
}
```

Khác biệt: **`self` phải viết tay** ở tham số đầu của *mọi* method (Java có `this` ngầm), và
**không khai báo field trước** — gán `self._embedder = ...` là field ra đời.

`top_k or get_settings().rag_top_k` = "lấy `top_k`, nếu nó None/0 thì lấy mặc định từ config" —
thành ngữ Python rất hay gặp (≈ `top_k != null ? top_k : default`).

### 8.2 Kế thừa & `super()`

```python
# ai-service/app/tools/schemas.py:20
class CamelModel(BaseModel):        # ← kế thừa: class CamelModel extends BaseModel
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")

# ai-service/app/tools/schemas.py:43
class ClubResponse(CamelModel):     # kế thừa tiếp
    id: UUID
    name: str
```

```python
# ai-service/app/assistant/limits.py:41
class LimitExceeded(Exception):
    def __init__(self, reason: str, message: str) -> None:
        self.reason = reason
        self.message = message
        super().__init__(f"{reason}: {message}")     # gọi constructor lớp cha
```

### 8.3 `@classmethod` / `@staticmethod`

```python
# ai-service/app/assistant/models.py:47
@field_validator("time_from", "time_to")
@classmethod
def _strip_tzinfo(cls, v: time | None) -> time | None:
```

`@classmethod` = method của **class**, tham số đầu là `cls` (chính class) thay vì `self` (một đối tượng cụ thể).
`@staticmethod` thì không nhận cả `self` lẫn `cls` — chỉ là hàm nằm trong class cho gọn.

### 8.4 `@dataclass` — class chỉ để chứa dữ liệu

```python
# ai-service/app/assistant/limits.py:17
@dataclass(frozen=True)
class Caps:
    max_turns_per_session: int
    token_budget_per_session: int
    max_tool_calls_per_turn: int
    ...
```

`@dataclass` tự sinh `__init__`, so sánh `==`, in ấn đẹp — bạn khỏi gõ. `frozen=True` = **bất biến**
(gán lại `caps.max_react_steps = 99` sẽ lỗi) ≈ `record` của Java 21.

> **Khi nào `@dataclass`, khi nào Pydantic `BaseModel`?** Dữ liệu **nội bộ, tự sinh, tin được** → `dataclass`
> (nhẹ). Dữ liệu **từ ngoài vào** (JSON của service khác, output LLM, body request) → Pydantic, vì nó
> **validate + ép kiểu** thật. Repo tuân đúng ranh giới này.

### 8.5 Exception tự định nghĩa

```python
# ai-service/app/tools/http_client.py:19
class ToolError(Exception):
    def __init__(self, status_code: int, code: str | None, message: str) -> None:
        self.status_code = status_code
        self.code = code
        self.message = message
        super().__init__(f"[{status_code}] {code}: {message}")
```

Kế thừa `Exception` là đủ để `raise ToolError(...)`. Việc mang theo `status_code`/`code` cho phép chỗ bắt
lỗi phản ứng khác nhau với 403 (chưa xác thực email) và 409 (ô đã bị người khác giữ).

---

## 9. Decorator — dấu `@`

### 9.1 Bản chất

Decorator là **một hàm nhận hàm khác và trả về hàm đã được bọc thêm**. Cú pháp `@`:

```python
@lru_cache
def get_settings() -> Settings:
    return Settings()
```

chính xác tương đương:

```python
def get_settings() -> Settings:
    return Settings()
get_settings = lru_cache(get_settings)      # ← chỉ là gán lại tên!
```

Hiểu vậy là hết bí ẩn: `@X` = "lấy hàm/class ngay dưới, đưa cho `X` xử lý, dùng kết quả thay cho nó".
Về vai trò, nó giống `@Transactional`/`@Scheduled` của Spring — nhưng ở Python **decorator chỉ là code
bình thường**, không cần framework nào.

### 9.2 Các decorator có trong repo, từ dễ tới khó

**`@lru_cache` — nhớ kết quả** (`app/config.py:89`):

```python
@lru_cache
def get_settings() -> Settings:
    return Settings()
```

Lần gọi đầu đọc file `.env` và dựng `Settings`; **mọi lần sau trả lại y hệt vật thể cũ** (không đọc lại file).
Đây là cách repo có "singleton config" mà không cần framework. Test muốn ép đọc lại thì gọi
`get_settings.cache_clear()` (`tests/conftest.py:20`).

**`@dataclass(frozen=True)`** (`limits.py:17`) — đã nói ở §8.4: sinh sẵn constructor + khoá bất biến.

**`@field_validator` — luật kiểm tra của Pydantic** (`models.py:47`):

```python
@field_validator("time_from", "time_to")
@classmethod
def _strip_tzinfo(cls, v: time | None) -> time | None:
    return v.replace(tzinfo=None) if v is not None and v.tzinfo is not None else v
```

Mỗi lần một `BookingIntent` được tạo, Pydantic tự gọi hàm này cho 2 trường đó để chuẩn hoá giá trị.
(Xếp chồng 2 decorator: `@field_validator` bọc ngoài `@classmethod`.)

**`@router.post` — khai báo endpoint** (`api/assistant.py:112`):

```python
@router.post("/sessions")
async def create_session(claims: UserClaims) -> dict:
    return {"sessionId": sessions.create(claims.get("sub", ""))}
```

≈ `@PostMapping("/sessions")` của Spring. Decorator **đăng ký** hàm này vào bảng route của FastAPI.

**`@app.exception_handler`** (`main.py:145`) ≈ `@RestControllerAdvice` — biến mọi `HTTPException` thành
JSON đúng khuôn chung của nền tảng `{code, message, timestamp}`.

**`@pytest.fixture`** (`tests/conftest.py:25`) — xem §11.3.

**`@respx.mock`** (`tests/tools/test_read_tools.py:14`) — bật chế độ giả lập HTTP cho test đó.

---

## 10. async / await

### 10.1 Vấn đề nó giải quyết

`ai-service` hầu như **không tính toán gì nặng** — nó chỉ **ngồi chờ**: chờ gateway trả lịch sân, chờ
Postgres, chờ Redis, chờ LLM sinh chữ (vài giây!). Nếu mỗi lần chờ mà khoá luôn một luồng thì vài chục
người dùng là nghẽn.

`async`/`await` cho phép: **trong lúc chờ, chuyển sang phục vụ request khác**. Về ý tưởng giống WebFlux/
`CompletableFuture` bên Java, nhưng cú pháp dễ đọc hơn nhiều (vẫn viết tuần tự từ trên xuống).

### 10.2 Ba từ khoá

```python
# ai-service/app/assistant/knowledge.py:60
async def search_knowledge(self, query: str) -> list[KnowledgeHit]:     # ①
    vector = await self._embedder.embed_query(query)                    # ②
    return await self._store.search(vector, self._top_k)
```

① `async def` = hàm bất đồng bộ ("coroutine").
② `await` = "chờ ở đây, nhường CPU cho việc khác, xong thì quay lại".

**Luật vàng — nhớ 2 điều:**

1. **Chỉ `await` được bên trong `async def`.**
2. **Gọi `async def` mà quên `await` thì hàm KHÔNG chạy** — bạn nhận về một đối tượng coroutine vô dụng
   (Python sẽ cảnh báo `coroutine ... was never awaited`). Đây là lỗi #1 của người mới.

### 10.3 `async with` — mở/đóng tài nguyên bất đồng bộ

```python
# ai-service/app/tools/http_client.py:59
async with httpx.AsyncClient(
    base_url=settings.gateway_url, timeout=settings.http_timeout_seconds
) as client:
    resp = await client.request(method, path, params=params, json=json, headers=headers)
```

≈ try-with-resources của Java: rời khối là client tự đóng, kể cả khi có lỗi.

### 10.4 Chạy nền & timeout

```python
# ai-service/app/main.py:39
app.state.sweeper = asyncio.create_task(_session_sweeper(settings))
```

`create_task` = chạy song song ở nền, **không chờ** (≈ `@Scheduled` dọn dẹp định kỳ). Vòng lặp
`while True` trong `_session_sweeper` (`main.py:64`) cứ vài phút lại quét phiên hết hạn.

```python
# app/assistant/nodes.py — mẫu dùng khắp nơi
await asyncio.wait_for(<lời gọi>, timeout=caps.llm_timeout_seconds)
```

`wait_for` = "chờ tối đa N giây, quá thì ném `TimeoutError`". Bắt buộc với LLM/HTTP, vì không giới hạn thì
một lượt chat có thể treo mãi.

### 10.5 Bài học mang đi

Trong app async **tuyệt đối không dùng thư viện chặn** (`requests`, `time.sleep`, JDBC kiểu đồng bộ) —
một lời gọi chặn sẽ **đứng cả service**, không riêng request đó. Đó là lý do repo chọn `httpx` (async),
`sqlalchemy[asyncio]`, `redis.asyncio`.

---

## 11. Context manager & `yield`

### 11.1 `with` là gì

"Mở — dùng — chắc chắn đóng". Repo dùng ở `async with httpx.AsyncClient(...)` (§10.3) và
`async with self._sessionmaker() as session` (`knowledge.py:46`).

### 11.2 Tự tạo bằng `@asynccontextmanager` — vòng đời service

Đây là đoạn quan trọng nhất của `main.py`:

```python
# ai-service/app/main.py:28
@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    log.info("ai-service.starting", port=settings.service_port, provider=settings.llm_provider)
    await eureka.register()             # ── mọi thứ TRƯỚC yield: chạy lúc KHỞI ĐỘNG
    await _setup_graph(app, settings)
    _setup_hardening(app, settings)
    app.state.sweeper = asyncio.create_task(_session_sweeper(settings))
    yield                               # ←── service SỐNG ở đây, phục vụ request
    await _teardown(app)                # ── mọi thứ SAU yield: chạy lúc TẮT
    await _teardown_graph(app)
    await eureka.deregister()
    log.info("ai-service.stopped")
```

Đọc là: nửa trên = `@PostConstruct` (đăng ký Eureka, dựng graph, mở Redis), `yield` = service đang chạy,
nửa dưới = `@PreDestroy` (huỷ task nền, đóng pool, rời Eureka). Một hàm gói trọn vòng đời — rất Pythonic.

### 11.3 `yield` trong test fixture

```python
# ai-service/tests/conftest.py:25
@pytest.fixture(autouse=True)
def auth_context():
    """Populate the request-scoped auth context so tools can forward a Bearer token."""
    tok = current_bearer.set(TEST_BEARER)      # ← setup: chạy TRƯỚC mỗi test
    cl = current_claims.set({...})
    yield                                      # ← test chạy ở đây
    current_bearer.reset(tok)                  # ← teardown: chạy SAU mỗi test
    current_claims.reset(cl)
```

Y hệt `@BeforeEach` + `@AfterEach` của JUnit gộp trong một hàm. `autouse=True` = **tự áp dụng cho mọi test**,
không cần khai báo gì thêm.

### 11.4 `yield` trong hàm thường (generator)

```python
# ai-service/app/db.py:46
async def get_session() -> AsyncIterator[AsyncSession]:
    async with get_sessionmaker()() as session:
        yield session
```

Hàm có `yield` không `return` một lần rồi hết, mà **sinh ra giá trị rồi tạm dừng**. Ở đây: giao session
cho người dùng, chờ họ xong, rồi khối `async with` đóng session lại.

### 11.5 `try / finally`

```python
# ai-service/app/tools/http_client.py:58,74
try:
    ...gọi HTTP...
finally:
    _audit_call(method, path, ..., int((time.monotonic() - t0) * 1000))
```

`finally` **luôn chạy**, kể cả khi ở giữa đã `raise` hay `return`. Nhờ vậy mọi lời gọi ra ngoài đều được
ghi audit — **thành công hay thất bại đều ghi**.

---

## 12. Exception & triết lý "degrade, đừng chết"

### 12.1 Cú pháp

```python
# ai-service/app/security/deps.py:32
try:
    claims = jwt.verify(token)
except jwt.JwtError as exc:
    log.warning("auth.token_invalid", error=str(exc))
    raise HTTPException(401, detail=error_body("TOKEN_INVALID", "Token không hợp lệ")) from exc
```

| Python | Java |
|---|---|
| `raise X(...)` | `throw new X(...)` |
| `except X as exc:` | `catch (X exc)` |
| `finally:` | `finally` |
| `raise Y(...) from exc` | `throw new Y(..., exc)` (giữ nguyên nhân gốc) |

`from exc` rất đáng dùng: log sẽ hiện cả chuỗi "lỗi B, gây ra bởi lỗi A". Xem thêm `api/assistant.py:86`.

### 12.2 `except Exception` xuất hiện khắp nơi — cố ý hay ẩu?

**Cố ý.** Đây là nguyên tắc thiết kế của service này, không phải cẩu thả:

```python
# ai-service/app/main.py:126
except Exception as exc:  # noqa: BLE001 — degrade to MemorySaver, keep the service up
    log.error("checkpointer.postgres_failed_fallback_memory", exc_type=type(exc).__name__, ...)
    set_default_graph(build_default_graph())
```

Postgres chết → **không sập service**, chỉ chuyển sang lưu tạm trong RAM và **log ERROR** để người vận hành biết.
Cùng tinh thần: Redis chết → cho request đi qua ("fail-open", `rate_limit.py:35`); ghi audit lỗi → không làm
hỏng lượt chat (`http_client.py:42`).

Ba điều làm nó thành *kỹ thuật đúng* thay vì *nuốt lỗi*:

1. **Luôn log** (thường mức ERROR) — không bao giờ `except: pass` câm lặng ở đường quan trọng.
2. **Có comment nói rõ vì sao** được phép bắt rộng.
3. **Có đường degrade cụ thể** (MemorySaver, cho qua, bỏ audit) chứ không đoán mò.

> `# noqa: BLE001` = "linter đừng cảnh báo lỗi mã BLE001 (bắt exception quá rộng) ở dòng này". Hiện repo
> mới bật nhóm luật `E, F, I, UP, B` (`pyproject.toml:70`) nên dòng này chủ yếu là **ghi chú chủ ý cho
> người đọc**. Còn `# noqa: E402` ở `tests/conftest.py:15` thì có tác dụng thật (E đang bật): nó cho phép
> đặt `import` sau đoạn set biến môi trường — vốn là điều linter cấm.

---

## 13. Dữ liệu & vòng lặp

### 13.1 List comprehension

Cách viết "một vòng lặp thành một dòng" — cực kỳ phổ biến, phải đọc quen:

```python
# ai-service/app/tools/booking_tools.py:74
return [schemas.PricingRuleResponse.model_validate(x) for x in resp.json()]
```

Đọc từ giữa ra: *"với mỗi `x` trong `resp.json()`, tạo `PricingRuleResponse` từ `x`, gom hết thành list"*.
Tương đương:

```python
result = []
for x in resp.json():
    result.append(schemas.PricingRuleResponse.model_validate(x))
return result
```

Có thể kèm điều kiện (`nodes.py:157`):

```python
return [f for f in MANDATORY if getattr(intent, f) is None]
#      ^^^^ lấy gì        ^^^^^^^^^^^^ duyệt gì   ^^^^^^^^^ lọc gì
```

### 13.2 Dict comprehension

```python
# ai-service/app/tools/booking_tools.py:28
params = {
    k: v
    for k, v in {
        "district": district,
        "sport": sport,
        "lat": lat,
        "lng": lng,
        "radius": radius,
    }.items()
    if v is not None                    # ← chỉ giữ tham số người dùng thật sự truyền
}
```

Dựng một dict tạm gồm 5 khoá rồi **loại sạch khoá có giá trị None** → URL cuối cùng chỉ mang tham số cần thiết.
`.items()` = duyệt cả khoá lẫn giá trị (`k, v` là "unpacking" — tách cặp thành 2 biến).

### 13.3 Vòng lặp lồng + `.append()`

```python
# ai-service/app/tools/booking_tools.py:53
available: list[schemas.AvailableSlot] = []
for court in grid.courts:
    for slot in court.slots:
        if slot.status == "AVAILABLE":
            available.append(schemas.AvailableSlot(court_id=court.id, ..., slot_id=slot.id, ...))
return available
```

Duyệt lưới sân 3 tầng (ngày → sân → ô 30 phút), giữ lại ô còn trống. Chú ý: `court_id` lấy từ **sân**,
`slot_id` lấy từ **ô** — dòng comment tại `schemas.py:57` giải thích cái bẫy JSON ở đây.

### 13.4 Xử lý chuỗi

```python
# ai-service/app/security/deps.py:31
token = header.removeprefix("Bearer ").strip()      # cắt tiền tố + bỏ khoảng trắng 2 đầu

# ai-service/app/assistant/vi_parse.py:20
text = text.replace("đ", "d").replace("Đ", "D")     # nối chuỗi lời gọi

# ai-service/app/assistant/vi_parse.py:22
return "".join(c for c in nfkd if unicodedata.category(c) != "Mn").lower()
#      ^^^^^^^^ ghép các ký tự lại thành chuỗi (bỏ dấu tiếng Việt)
```

### 13.5 `dict` làm bảng tra + regex

```python
# ai-service/app/assistant/vi_parse.py:28
_WEEKDAYS: dict[str, int] = {
    "thu 2": 0, "t2": 0, "thu hai": 0,
    "thu 6": 4, "t6": 4, "thu sau": 4,
    "chu nhat": 6, "cn": 6,
}

# ai-service/app/assistant/vi_parse.py:47
if re.search(r"\bhom nay\b", t):
    return today
```

`r"..."` = "raw string" — dấu `\` giữ nguyên, bắt buộc khi viết regex. `re.search` tìm mẫu trong chuỗi.
File này là ví dụ đẹp: **những gì liên quan tới ngày/giờ/tiền được xử lý bằng code tất định**, không giao cho LLM.

### 13.6 `is None` vs `== None`

Với `None` luôn dùng `is` / `is not` (so sánh **cùng một vật thể**), không dùng `==`:

```python
if value is None: ...           # ✅ chuẩn
if self._client is None: ...    # embeddings.py:35
```

---

## 14. State cấp module: `global`, cache, ContextVar

### 14.1 Biến cấp module + `global` — singleton lười

```python
# ai-service/app/db.py:26
_engine: AsyncEngine | None = None
_sessionmaker: async_sessionmaker[AsyncSession] | None = None


def get_engine() -> AsyncEngine:
    global _engine, _sessionmaker           # ← "tôi sẽ GÁN LẠI biến cấp module, không tạo biến mới"
    if _engine is None:
        settings = get_settings()
        _engine = create_async_engine(settings.ai_db_url, pool_pre_ping=True)
        _sessionmaker = async_sessionmaker(_engine, expire_on_commit=False)
    return _engine
```

Biến khai báo ngoài mọi hàm = **`static` field** của module. Từ khoá `global` **bắt buộc** khi muốn *gán lại*
nó từ trong hàm (chỉ đọc thì không cần).

Mẫu này = "lazy singleton": engine chỉ được tạo ở **lần gọi đầu tiên**. Nhờ vậy `import app.db` trong unit test
**không mở kết nối DB nào** — đúng như docstring dòng 3 nói.

### 14.2 `@lru_cache` — cách khác để có singleton

`app/config.py:89` (§9.2). Ngắn hơn `global`, dùng khi hàm không tham số và kết quả bất biến.

### 14.3 `ContextVar` — "ThreadLocal cho async"

```python
# ai-service/app/security/context.py:13
current_bearer: ContextVar[str | None] = ContextVar("current_bearer", default=None)
current_claims: ContextVar[dict | None] = ContextVar("current_claims", default=None)


def set_auth(bearer: str, claims: dict) -> None:
    current_bearer.set(bearer)
    current_claims.set(claims)


def get_bearer() -> str:
    token = current_bearer.get()
    if not token:
        raise RuntimeError("No bearer token in context — a tool was called outside an authenticated request")
    return token
```

**Vấn đề nó giải**: mọi tool đều phải gửi kèm JWT của đúng người dùng đang chat. Nếu truyền tham số `token`
qua từng lớp thì mọi chữ ký hàm đều bẩn (`get_day_grid(club_id, date, sport, token)`…).

**Cách giải**: đầu request, `require_user` (`deps.py:40`) cất token vào ContextVar. Sâu bên dưới,
`http_client.py:54` lấy ra dùng:

```python
headers = {"Authorization": f"Bearer {get_bearer()}"}
```

Giá trị được cô lập **theo từng request/task** — 2 người chat cùng lúc không lẫn token của nhau (đây chính
là chỗ `ThreadLocal` của Java **không** làm được với async, còn `ContextVar` thì có).

---

## 15. Thấy dòng này thì hiểu là gì

Bảng tra nhanh các thư viện. Mục tiêu chỉ là **đọc hiểu**, không phải thuộc API.

### Pydantic v2 — "DTO có kiểm tra"

| Thấy | Nghĩa |
|---|---|
| `class X(BaseModel)` | khai báo một khuôn dữ liệu có **validate lúc chạy** |
| `id: UUID` | trường bắt buộc, JSON `"abc-123"` sẽ được **ép** thành `UUID` (sai định dạng → lỗi rõ ràng) |
| `price: Decimal \| None = None` | trường tuỳ chọn |
| `Field(ge=0, le=100_000_000)` | chặn khoảng giá trị (`models.py:41` — chặn "ngân sách vô hạn") |
| `Field(default_factory=list)` | mặc định là **list mới mỗi lần** (né bẫy §7.1) |
| `model_validate(json_dict)` | JSON/dict → đối tượng (≈ `objectMapper.readValue`) |
| `model_dump()` | đối tượng → dict (≈ `writeValueAsString`) |
| `model_dump(by_alias=True)` | xuất ra **camelCase** cho FE (`api/assistant.py:96`) |
| `model_config = ConfigDict(alias_generator=to_camel)` | đọc JSON `startTime` vào field `start_time` (`schemas.py:20`) |
| `extra="ignore"` | service Java thêm field mới → **không vỡ** parse |
| `@field_validator("x")` | luật kiểm tra/chuẩn hoá riêng cho trường `x` |

Vì sao quan trọng ở đây: LLM và service khác đều là "nguồn không tin được" — Pydantic là **hàng rào** ở biên.

**pydantic-settings** (`config.py:14`) là bản mở rộng: các field được nạp từ **biến môi trường / file `.env`**.

```python
jwt_secret: str        # config.py:69 — KHÔNG có giá trị mặc định
```

→ thiếu `JWT_SECRET` thì service **chết ngay lúc khởi động** thay vì lỗi mơ hồ lúc chạy (fail fast, cố ý).

### FastAPI — web framework

| Thấy | Nghĩa (đối chiếu Spring) |
|---|---|
| `app = FastAPI(...)` (`main.py:142`) | `@SpringBootApplication` |
| `router = APIRouter(prefix="/api/ai/assistant")` (`api/assistant.py:41`) | `@RequestMapping` cấp class |
| `@router.post("/{session_id}/messages")` | `@PostMapping` |
| `session_id: str` trong tham số | lấy từ path (≈ `@PathVariable`) |
| `req: MessageRequest` (kiểu BaseModel) | lấy từ body + validate (≈ `@RequestBody @Valid`) |
| `claims: UserClaims` (Annotated + Depends) | DI (≈ `@Autowired` / argument resolver) |
| `raise HTTPException(404, detail=...)` | trả lỗi HTTP |
| `app.state.xxx` (`main.py:49`) | chỗ cất object dùng chung toàn app (≈ bean singleton) |
| `EventSourceResponse(...)` | **SSE** — đẩy dần từng phần trả lời về trình duyệt |
| `lifespan=lifespan` (`main.py:142`) | vòng đời khởi động/tắt (§11.2) |

**`Depends` — DI kiểu FastAPI** (`api/assistant.py:59-69`):

```python
def get_audit_sink(request: Request):
    return getattr(request.app.state, "audit_sink", None) or audit.NullAuditSink()

AuditSinkDep = Annotated[Any, Depends(get_audit_sink)]
```

Endpoint chỉ cần khai `sink: AuditSinkDep` là FastAPI tự gọi `get_audit_sink` và truyền kết quả vào.
Test thì thay bằng hàng fake qua `dependency_overrides` — chính là "inject mock" của Spring.

### httpx — HTTP client

`async with httpx.AsyncClient(base_url=..., timeout=...) as client:` → `await client.request(...)`.
`resp.status_code`, `resp.json()`, `resp.text`. (`http_client.py:59`)

### SQLAlchemy 2 + Alembic — "JPA + Flyway"

```python
# ai-service/app/models/user_preference.py:16
class UserPreference(Base):
    __tablename__ = "user_preferences"

    id: Mapped[uuid.UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(PGUUID(as_uuid=True), unique=True, index=True, nullable=False)
    preferred_sport: Mapped[str | None] = mapped_column(String(50))
```

`Mapped[T]` + `mapped_column(...)` ≈ `@Column` của JPA. `class X(Base)` ≈ `@Entity`.
Truy vấn dùng `select(...)` (`knowledge.py:41`) thay cho JPQL.

`migrations/versions/000X_*.py` = **Flyway migration**, chạy bằng `uv run alembic upgrade head`.

### structlog — log có cấu trúc

```python
# ai-service/app/main.py:31
log.info("ai-service.starting", port=settings.service_port, provider=settings.llm_provider)
```

Khác Java ở chỗ: **không ghép chuỗi**. Tham số đầu là **tên sự kiện**, phần còn lại là **cặp khoá=giá trị**.
Kết quả ra JSON, máy đọc/lọc được:

```json
{"event": "ai-service.starting", "port": 3010, "provider": "ollama", "level": "info", "timestamp": "..."}
```

`app/logging.py` còn cắm thêm bộ lọc `redact_pii` để **che số điện thoại trước khi ghi log**.

### pytest — test

| Thấy | Nghĩa (đối chiếu JUnit) |
|---|---|
| file `tests/**/test_*.py`, hàm `def test_...` | pytest tự tìm theo tên (không cần `@Test`) |
| `assert page.total_elements == 1` | `assertEquals(...)` — dùng `assert` trần, pytest tự in diff đẹp |
| `tests/conftest.py` | cấu hình + fixture **dùng chung**, tự động nạp |
| `@pytest.fixture` | `@BeforeEach`/`@AfterEach` (§11.3) |
| `@pytest.mark.parametrize` | test chạy lặp với nhiều bộ dữ liệu |
| `asyncio_mode = "auto"` (`pyproject.toml:58`) | cho phép viết thẳng `async def test_...` |
| `@respx.mock` + `respx.get(...).mock(...)` | giả lập HTTP — test không cần gateway thật |
| `class FakeModel` (`tests/assistant/_fakes.py:31`) | thay cho Mockito: **viết tay** class giả, hợp `Protocol` (§6.5) |

Ví dụ đầy đủ, đọc được ngay:

```python
# ai-service/tests/tools/test_read_tools.py:14
@respx.mock
async def test_search_clubs_forwards_jwt_and_parses():
    route = respx.get(f"{h.BASE}/api/clubs").mock(return_value=httpx.Response(200, json=h.club_page()))
    page = await booking_tools.search_clubs(sport="PICKLEBALL")

    assert page.total_elements == 1
    assert page.content[0].name == "An Bình Pickleball"
    req = route.calls.last.request
    assert req.headers["Authorization"] == f"Bearer {TEST_BEARER}"
```

Dịch: giả vờ `GET /api/clubs` trả JSON mẫu → gọi tool thật → kiểm tra (a) parse đúng, (b) **có gửi kèm JWT**.

### LangGraph — chỉ cần hiểu tới mức này

- **node** = một **hàm async** nhận `state` (chính là `AgentState`, §6.4) và trả về **dict các thay đổi**:

  ```python
  # ai-service/app/assistant/nodes.py:172
  async def route(self, state: AgentState) -> dict:
      return {}
  ```

- **graph** = ráp các node lại + nối cạnh (`graph.py:43-71`): `route → perceive → memory_load → … → human_review`.
- **conditional edge** = rẽ nhánh bằng một hàm (`sg.add_conditional_edges("route", route_decision, {...})`).
- **interrupt** = **dừng graph giữa chừng chờ người bấm xác nhận** — đây là chốt an toàn tiền của cả feature.
- **checkpointer** = lưu state để lượt chat sau còn nhớ (`MemorySaver` = RAM, `AsyncPostgresSaver` = DB).

Vì sao thiết kế vậy → đọc `../usecase/UC_AI_Service_CustomerSupport.md` §18.

---

## 16. 3 đường đi xuyên code

Cách học nhanh nhất: bám theo **một luồng dữ liệu**, mở đúng từng file theo thứ tự.

### (a) Dễ — `GET /health` trả `{"status":"UP"}`

```
uv run uvicorn app.main:app --port 3010
        │
        ├─ 1. nạp module app/main.py
        │      · dòng 24: configure_logging()
        │      · dòng 165: app = create_app()          ← uvicorn tìm đúng biến tên "app"
        │
        ├─ 2. create_app()  (main.py:141)
        │      · FastAPI(..., lifespan=lifespan)
        │      · @app.get("/health") → đăng ký route   (main.py:157)
        │      · app.include_router(assistant_router)  (main.py:161)
        │
        ├─ 3. lifespan nửa TRÊN chạy   (main.py:30-39): Eureka → graph → Redis → task nền
        │
        └─ 4. curl http://localhost:3010/health → hàm health() → {"status": "UP"}
```

**Việc cho bạn**: mở `app/main.py`, chỉ ra dòng nào là "trước khi phục vụ" và dòng nào là "lúc tắt".

### (b) Vừa — một tool đọc lịch sân

```
booking_tools.get_day_grid(club_id, date, sport="PICKLEBALL")     (booking_tools.py:43)
   │
   ├─ validate.sport(sport, required=False)          (validate.py:32)  ← chặn giá trị bậy trước khi ra ngoài
   ├─ params = {"date": date.isoformat()}                            ← dựng query string
   │
   ├─ await request("GET", f"/api/clubs/{club_id}/slots", params=…)  (http_client.py:46)
   │       ├─ headers = {"Authorization": f"Bearer {get_bearer()}"}  ← JWT lấy từ ContextVar (§14.3)
   │       ├─ async with httpx.AsyncClient(base_url=gateway_url)     ← gọi qua GATEWAY, không hardcode host
   │       ├─ status >= 400 → raise ToolError(...)                   (http_client.py:72)
   │       └─ finally: _audit_call(...)                              ← luôn ghi audit
   │
   ├─ ClubGridResponse.model_validate(resp.json())   (schemas.py:77)  ← JSON → object có kiểu
   └─ 2 vòng for, giữ ô "AVAILABLE" → list[AvailableSlot]            (booking_tools.py:53)
```

Chỉ ~25 dòng nhưng chứa: type hint · async · `async with` · f-string · exception tự định nghĩa ·
`try/finally` · Pydantic · vòng lặp lồng · ContextVar. **Đọc kỹ đúng một luồng này là nắm được 70% tài liệu trên.**

### (c) Khó — một lượt chat

```
POST /api/ai/assistant/{id}/messages          (api/assistant.py)
   │
   ├─ Depends(require_user)      (security/deps.py:26)
   │      ├─ đọc header "Authorization"
   │      ├─ jwt.verify(token)                      → sai → HTTPException 401
   │      └─ set_auth(token, claims)                → cất vào ContextVar
   │
   ├─ rate limiter → quá hạn mức thì 429            (rate_limit.py:28)
   ├─ sessions.resolve(...)      → không có/hết hạn → 404 / 410
   │
   ├─ graph  (graph.py:38)
   │      route → perceive → memory_load → agent → rank_propose → human_review(DỪNG)
   │                │             │           └─ gọi tool ở (b) để lấy lịch thật
   │                │             └─ nạp thói quen đã học của user
   │                └─ vi_parse (CODE) quyết ngày/giờ/ngân sách — KHÔNG tin LLM
   │
   └─ EventSourceResponse → đẩy dần từng bước về FE (SSE)
```

Đọc theo thứ tự: `api/assistant.py` → `security/deps.py` → `graph.py` → `nodes.py`.

---

## 17. Bảng tra ký hiệu lạ

| Ký hiệu | Ở đâu | Nghĩa |
|---|---|---|
| `:` cuối dòng | `def f():`, `if x:` | mở khối lệnh (thay `{`) |
| `->` | `def f() -> str:` | kiểu trả về |
| `\|` trong type | `str \| None` | "hoặc" (union) |
| `*` đứng một mình | `def f(a, *, b)` | các tham số sau bắt buộc gọi theo tên (§7.2) |
| `*args` / `**kwargs` | — | nhận số lượng tham số tuỳ ý (vị trí / theo tên) |
| `@` | `@lru_cache` | decorator (§9) |
| `f"..."` | `f"Bearer {tok}"` | f-string (§5.3) |
| `r"..."` | `r"\bhom nay\b"` | raw string, cho regex (§13.5) |
| `_ten` | `_engine`, `_bad()` | nội bộ, đừng dùng từ ngoài |
| `__ten__` | `__init__`, `__name__` | tên đặc biệt của Python ("dunder") |
| `_` một mình | `for _ in range(3)` | "giá trị này tôi không dùng" |
| `...` | `async def search(...) -> ...: ...` | `Ellipsis` — thân rỗng trong `Protocol` (§6.5) |
| `# noqa: E402` | `tests/conftest.py:15` | bảo linter bỏ qua luật đó ở dòng này (§12.2) |
| `# type: ignore[...]` | `config.py:91` | bảo trình kiểm kiểu bỏ qua dòng này |
| `[T]` | `Page[T]`, `Mapped[str]` | generic (§6.3) |
| `100_000_000` | `models.py:41` | dấu `_` chỉ để dễ đọc số = `100000000` |
| `x or y` | `top_k or settings.rag_top_k` | "x nếu x có giá trị, ngược lại y" |
| `if __name__ == "__main__":` | `cli.py:101` | chỉ chạy khi file được **thực thi trực tiếp**, không chạy khi bị import (≈ `public static void main`) |

---

## 18. Tự học tiếp + 8 bài tập

### 18.1 Lệnh an toàn để nghịch

```bash
cd ai-service

uv run python                        # mở REPL — gõ thử từng dòng Python
uv run python -c "from app.config import get_settings; print(get_settings().service_port)"
uv run python -c "from app.assistant import vi_parse; import datetime; print(vi_parse.resolve_relative_date('tối thứ 6', datetime.date.today()))"

uv run pytest -q                     # toàn bộ test → "161 passed, 3 skipped" (offline, không cần DB/mạng/LLM)
uv run pytest tests/assistant/test_vi_parse.py -v    # chạy 1 file, in tên từng test
uv run pytest -k "grid" -v           # chạy các test có chữ "grid" trong tên
uv run ruff check .                  # lint
```

> Ba lệnh `pytest` trên **hoàn toàn không đụng vào DB, Redis, LLM hay service Java nào** — nhờ đúng kỹ thuật
> `Protocol` + fake ở §6.5. Cứ chạy thoải mái.

Muốn xem "cái gì đang chạy", chèn tạm `print(...)` vào code rồi chạy lại test — cách học nhanh và an toàn
(nhớ xoá trước khi commit).

### 18.2 Tám bài tập (có đáp án)

Mở đúng file được trỏ tới, tự trả lời trước rồi mới xem đáp án.

**1.** `app/api/__init__.py` rỗng hoàn toàn. Xoá nó đi thì có sao không, vì sao repo vẫn giữ?
<details><summary>Đáp án</summary>

Nó đánh dấu `app/api` là package. Python 3.3+ vẫn import được khi thiếu (namespace package) nhưng dễ sinh lỗi
tinh vi với test-discovery/tooling; giữ lại là quy ước chuẩn và tường minh. Xem §4.2.
</details>

**2.** `app/models/__init__.py` import 4 class rồi **không dùng** — sao không xoá cho gọn?
<details><summary>Đáp án</summary>

Alembic chỉ "thấy" bảng khi class model đã được import ít nhất một lần (đăng ký vào `Base.metadata`).
Xoá đi thì migration sẽ không sinh/nhận ra bảng. Docstring dòng 1 của file nói đúng điều đó.
</details>

**3.** `app/config.py:89` có `@lru_cache`. Bỏ nó đi thì điều gì thay đổi?
<details><summary>Đáp án</summary>

Mỗi lần gọi `get_settings()` sẽ đọc lại file `.env` và dựng `Settings` mới — chậm, và mất tính "một nguồn
cấu hình duy nhất". `@lru_cache` biến nó thành singleton lười.
</details>

**4.** `app/tools/http_client.py:46` có dấu `*` đứng một mình giữa danh sách tham số. Nó làm gì?
<details><summary>Đáp án</summary>

Ép `params` và `json` **phải** được truyền theo tên. `request("GET", "/x", {...})` sẽ lỗi TypeError; phải viết
`request("GET", "/x", params={...})`. Xem §7.2.
</details>

**5.** `app/db.py:31` có `global _engine, _sessionmaker`. Bỏ dòng `global` đi thì sao?
<details><summary>Đáp án</summary>

Không có `global`, phép gán `_engine = create_async_engine(...)` sẽ tạo một **biến cục bộ mới trong hàm** thay vì
gán cho biến cấp module. Kết quả: biến module `_engine` mãi mãi là `None`, mỗi lần gọi lại tạo engine mới —
rò rỉ kết nối. Xem §14.1.
</details>

**6.** `app/assistant/knowledge.py:27` khai báo `class KnowledgeStore(Protocol)`, nhưng
`PgvectorKnowledgeStore` (dòng 31) **không** viết `class PgvectorKnowledgeStore(KnowledgeStore)`. Vì sao vẫn dùng được?
<details><summary>Đáp án</summary>

`Protocol` là structural typing: chỉ cần class **có đủ method đúng chữ ký** (`async def search(self, query_vector, top_k)`)
là hợp lệ, không cần khai báo kế thừa. Nhờ vậy `FakeKnowledgeStore` trong test cũng "vừa khuôn" mà không phụ thuộc
gì vào code thật. Xem §6.5.
</details>

**7.** `app/tools/booking_tools.py:28` dựng `params` bằng dict comprehension có `if v is not None`.
Chuyện gì xảy ra nếu bỏ điều kiện đó?
<details><summary>Đáp án</summary>

Mọi tham số không truyền sẽ vẫn được gắn vào URL với giá trị `None` → query string kiểu
`?district=None&lat=None` gửi sang gateway → court-service nhận rác, có thể lọc sai hoặc lỗi 400.
Điều kiện đó chỉ giữ lại tham số người dùng thật sự nêu. Xem §13.2.
</details>

**8.** `app/main.py:126` bắt `except Exception` rồi vẫn cho service chạy tiếp. Đây là "nuốt lỗi" hay có chủ đích?
<details><summary>Đáp án</summary>

Có chủ đích: Postgres chết → thay vì sập, service **hạ cấp** sang checkpointer trong RAM (`MemorySaver`) và
**log mức ERROR** để người vận hành biết. Ba dấu hiệu của "degrade đúng cách" đều có: log ERROR + comment nêu lý do
+ đường lui cụ thể. Xem §12.2.
</details>

### 18.3 Học tiếp theo thứ tự nào

1. **Làm chắc §4 → §9** (package/import, type hint, class, decorator) — đây là phần bạn sẽ dùng mỗi ngày.
2. **Đọc lại đường đi (b)** ở §16 cho tới khi không cần tra bảng nữa.
3. **Sang §10-11 (async)** — khó nhất với người mới, nhưng chỉ cần nắm "await = chờ mà không chặn service"
   và "quên `await` thì hàm không chạy" là đủ đọc code.
4. **Rồi mới mở `app/assistant/nodes.py`** (688 dòng). Đọc từng method của class `AssistantNodes` một, mỗi
   method là một bước xử lý độc lập — đừng đọc từ trên xuống một mạch.
5. Khi đã đọc trôi code, quay sang `../usecase/UC_AI_Service_CustomerSupport.md` §18 để hiểu **vì sao** kiến trúc
   được thiết kế như vậy (phần đó dạy tư duy hệ thống AI, không dạy Python).

---

## Phụ lục — 10 câu hỏi hay gặp

| Câu hỏi | Trả lời ngắn |
|---|---|
| Vì sao không thấy `public`/`private`? | Python không có. Quy ước: `_ten` = nội bộ (§5.4). |
| Vì sao không cần khai báo kiểu biến? | Không bắt buộc; type hint là tuỳ chọn, không kiểm lúc chạy (§6.1). |
| `self` là gì, sao phải viết? | = `this` của Java, nhưng Python bắt viết tay ở tham số đầu mỗi method (§8.1). |
| `__init__.py` vs `__init__`? | File đánh dấu package vs constructor của class — không liên quan nhau (§4.2, §8.1). |
| Sao có file `.pyc` trong `__pycache__`? | Bytecode Python tự sinh để chạy nhanh hơn; không đụng, không commit (§4.3). |
| `uv` khác `pip` chỗ nào? | `uv` làm cả việc của `pip` + `venv` + quản lý bản Python, nhanh hơn nhiều (§3). |
| Sao phải `uv run pytest` mà không `pytest`? | `uv run` chạy trong `.venv` của project; gõ trần sẽ dùng Python hệ thống, thiếu thư viện (§3). |
| Test chạy được mà không cần DB/mạng? | Nhờ `Protocol` + fake + `respx` — mọi phụ thuộc ngoài đều thay được (§6.5, §15). |
| Sao mỗi hàm đều `async`? | Service này chủ yếu **chờ** I/O (HTTP, DB, LLM); async cho phép phục vụ nhiều request trong lúc chờ (§10.1). |
| Sửa code xong kiểm thế nào? | `uv run ruff check .` rồi `uv run pytest -q` — phải xanh cả hai. |

---

_Tài liệu này chỉ dạy đọc code — không mô tả quy trình build/deploy. Cách chạy service: `README.md`.
Vì sao thiết kế kiến trúc như vậy: `../usecase/UC_AI_Service_CustomerSupport.md` §18._
