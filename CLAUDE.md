# BadmintonHub — CLAUDE.md

Microservices platform for badminton court booking, matchmaking, coach enrollment, and events in Vietnam.
Full design reference: `CLAUDE_Example.md` · ERDs: `ERD_All_Services.md` · Use Cases: `UC_*.md`

---

## Rules Index

Detailed rules live in `.claude/rules/`. Two files are always loaded; the rest auto-load by file type.

| File | Trigger | Covers |
|---|---|---|
| `architecture.md` | **always** | Module map · ports · 10 never-violate rules · build commands |
| `payment.md` | **always** | Bank QR flow · no VNPay · manual refund · Prepay+Escrow model |
| `java-spring.md` | `**/*.java` | Package structure · entities · DTOs · controllers · API conventions |
| `database.md` | `**/*.java`, `**/*.sql` | Cross-service UUID · soft delete · state machines · indexes |
| `kafka-patterns.md` | `**/*Kafka*.java` etc. | Topic registry · Outbox Pattern · idempotency · zombie check · DLQ |
| `redis-patterns.md` | `**/*Redis*.java` etc. | Key/TTL registry · distributed lock · atomic counter · fallback |
| `eureka-config.md` | `**/application.yml`, `**/pom.xml` | Server setup · client registration · `lb://` Gateway routing · Docker |
| `rbac-security.md` | `**/*Controller*.java` etc. | Roles · permissions · JWT config · `@PreAuthorize` patterns |
| `resilience.md` | `**/*Service*.java` etc. | Circuit breaker · Kafka retry · scheduler patterns · cleanup jobs |
| `frontend.md` | `frontend/src/**/*.{ts,tsx}` | React Query · Zustand · axiosClient · Socket.io · forms · routing |

---

## Session Progress

> Phần này được cập nhật tự động bằng lệnh `/handoff` cuối mỗi phiên làm việc.

**Cập nhật lần cuối**: 2026-06-08

### ✅ Đã hoàn thành
- Setup CLAUDE.md với rules index và quick reference
- Tạo 10 rule files trong `.claude/rules/` (architecture, payment, java-spring, database, kafka-patterns, redis-patterns, eureka-config, rbac-security, resilience, frontend)
- Tạo `IMPLEMENTATION_GUIDE.md` — kế hoạch 4 tuần từ scratch đến complete
- Tạo `.claude/commands/handoff.md` — slash command để bàn giao phiên
- **Day 1 HOÀN THÀNH** — parent `pom.xml` + 13 module scaffold + `docker-compose.yml`
  - `mvn clean install -DskipTests` → BUILD SUCCESS (14/14 modules)
  - `docker compose up -d` → 15/15 containers healthy (PG×9, Redis, Mongo, Kafka, Zookeeper, Zipkin, Elasticsearch)
- Viết lại `.gitignore` professional (Maven, Spring Boot, IDE, secrets, logs, frontend, Docker)
- Purge build artifacts khỏi git history bằng `git filter-repo` + force push thành công
- Fix lỗi startup `notification-service` và `ai-service`: thêm `autoconfigure.exclude` cho DataSourceAutoConfiguration + HibernateJpaAutoConfiguration
- **Environment setup HOÀN THÀNH** — tạo đầy đủ `.env` / `.env.example` + `frontend/.env` / `frontend/.env.example`
  - Thêm `spring-dotenv 3.0.0` vào parent `pom.xml` — tất cả 13 module kế thừa tự động
  - Update 11 `application.yml` → dùng `${VAR:default}` syntax (infra có default, secrets fail fast)
  - `.env` gitignored, `.env.example` sẵn sàng commit
- **Day 2 HOÀN THÀNH** — `common` module đầy đủ source + build pass
  - `common/src/main/java/com/badmintonhub/common/entity/BaseAuditEntity.java`
  - `common/.../exception/` — ApiException + 5 subclasses (ResourceNotFound, Conflict, Unauthorized, Forbidden, InvalidToken)
  - `common/.../dto/response/` — ErrorResponse record + PageResponse\<T\> record
  - `common/.../handler/GlobalExceptionHandler.java` — `@RestControllerAdvice`
  - `mvn clean install -DskipTests` → BUILD SUCCESS (14/14 modules, 11.5s)
- **`IMPLEMENTATION_GUIDE.md` nâng cấp** — thêm advanced Claude Code patterns (901 → 1147 dòng):
  - Advanced Setup: hook auto-compile, permission allowlist, MCP Postgres inspector
  - Git Worktrees + dependency graph + full workflow
  - Agent Team Patterns (Layer/Feature/TDD split)
  - ⚡ Parallel hints trên Day 8‖9, 13‖14, 20
  - Code Review Workflow + checklist trước merge
  - Troubleshooting nhanh (Kafka, Redis, Postgres, Zipkin, Eureka)
- **`IMPLEMENTATION_GUIDE.md` per-day upgrade** — thêm block `🚀 Bản nâng cấp — Pro Workflow` vào 7 ngày phức tạp (Day 4, 7, 8, 11, 12, 14, 15), giữ nguyên 100% nội dung cũ:
  - Mỗi block: agent team split cụ thể (ai làm gì + thứ tự dependency) + slash command (`/plan`, `/code-review`, `/security-review`, `/code-review ultra`) + worktree command (nơi áp dụng) + ⚠️ test bắt buộc
  - Day 4: Layer Split 3 agents · Day 7: Feature Split + race test · Day 8: worktree + Layer Split · Day 11: TDD Split (Outbox/zombie) · Day 12: cùng service Day 11 nên KHÔNG worktree · Day 14: worktree + 3 agents (postgres/ES/api) · Day 15: 3 agents + ultra review
  - Ngày scaffold đơn giản giữ nguyên theo quyết định của user
- **Phiên 2026-06-08 — Pro Claude Code workflow (commands + agents + guide executable)**:
  - **5 custom command MỚI** (`.claude/commands/`): `new-service`, `done-check`, `race-test`, `kafka-trace`, `redis-keys` → tổng **10 command**
  - **Tối ưu 4 command CŨ** thành project-aware: `explain-code`, `plan-feat`, `self-review`, `write-tests` (đọc rule + 10 Never Violate + Testcontainers no-H2 + package chuẩn; viết tiếng Việt cho đồng bộ). `self-review` đổi `git diff HEAD~1`→`git diff HEAD`; `write-tests` thu hẹp quyền `Bash`→`Bash(mvn/docker)`
  - **2 custom agent** (`.claude/agents/`): `spring-builder` (build track service đúng convention) + `test-writer` (test Testcontainers làm "spec", TDD). KHÔNG tạo reviewer/researcher (trùng `/code-review` + `Explore` built-in)
  - **`IMPLEMENTATION_GUIDE.md` overhaul (~1254 → ~1390 dòng)**: thêm section "Claude Code Toolbox" (bảng quyết định công cụ); viết lại "Agent Team — Công thức thực thi" (4 bước **bấm-theo-được**); chuẩn hoá **7 block Pro Workflow** về skeleton *Plan → Phase nền (commit) → N subagent / TDD → Chốt* với câu gõ cụ thể; sửa `/plan`→`/plan-feat`; fix lỗi worktree `.env` (gitignored, phải `cp` tay) + hook auto-compile (đọc `jq` stdin)
- **Phiên 2026-06-08 — Kafka UI thêm vào docker-compose.yml**:
  - Thêm `kafka-ui` (provectuslabs/kafka-ui:latest) vào `docker-compose.yml` — port **8080**, depends on `kafka` healthy
  - Kết nối qua internal listener `kafka:29092` (PLAINTEXT_INTERNAL), cluster name `badmintonhub-local`
  - UI dùng được để browse topics, produce/consume message test, monitor consumer group lag, xem DLT topic
- **Day 3 HOÀN THÀNH — `api-gateway` JWT auth + rate limit (verified end-to-end)**:
  - **Module mới `common-security`** (web/JPA-free, chỉ jjwt 0.12.6) — thêm vào parent `pom.xml` (modules + dependencyManagement + `<jjwt.version>`)
    - `common-security/.../security/JwtUtil.java` — verify HS256 thuần (parseAndValidate + getUserId/getJti/getRoles); `CLAIM_ROLES="roles"`; gateway **và** mọi downstream service tái dùng chung 1 class
  - **`api-gateway`** (reactive WebFlux, **KHÔNG** depend `common` vì kéo MVC+JPA xung đột Netty):
    - `filter/JwtAuthenticationFilter.java` — `GlobalFilter` `HIGHEST_PRECEDENCE`: skip `/api/auth/**` + `/actuator/**`; verify chữ ký/hạn; check Redis `session:blacklist:{jti}` (**fail-open** nếu Redis chết); stash userId vào exchange-attribute nội bộ; forward **CHỈ** `Authorization: Bearer` (KHÔNG emit `X-User-Id`/`X-User-Roles`); lỗi → 401 JSON `{code,message,timestamp}` (codes: `TOKEN_MISSING`/`TOKEN_EXPIRED`/`TOKEN_INVALID`/`TOKEN_REVOKED`)
    - `config/GatewayConfig.java` — bean `JwtUtil` + `RedisRateLimiter(2,100,1)` + `KeyResolver` (key=userId, fallback IP)
    - `application.yml` — thêm `default-filters: RequestRateLimiter` (built-in của Spring Cloud Gateway, token-bucket) áp cho cả 10 route
  - **Fix blocker toàn dự án**: `spring-boot:run` fork mỗi module với CWD = thư mục module → spring-dotenv không thấy `.env` ở root → `JWT_SECRET` unresolved. Pin `<workingDirectory>${session.executionRootDirectory}</workingDirectory>` trong parent pom pluginManagement → **mọi service** từ nay chạy đúng `.env` root
  - **Sửa rule docs cho khớp**: `rbac-security.md` (defense-in-depth, service re-validate, KHÔNG trust header) + `redis-patterns.md` (rate-limit key thật là `request_rate_limiter.*`)
  - **Verify trực tiếp** (gateway boot :3000 + Redis live): 7/7 pass — no-token→401 TOKEN_MISSING · bad-token→401 TOKEN_INVALID · `/api/auth/**` skip→503 no-instance · `/actuator/health`→200 · valid HS256→pass auth · blacklist jti→401 TOKEN_REVOKED · 130 req→26×429
  - **Đồng bộ docs theo kiến trúc Day 3** (sau khi build xong, sửa mọi file mô tả lệch):
    - `IMPLEMENTATION_GUIDE.md`: Day 3 đánh dấu ✅ + viết lại block đặc tả khớp code (common-security, public `/actuator/**`, forward chỉ Bearer, built-in RequestRateLimiter, fail-open, codes TOKEN_*, run từ root); Day 4 đổi `GatewayHeaderAuthFilter`→`JwtAuthFilter` re-validate + nhắc claim contract; đổi 4 chỗ `GatewayHeaderAuthFilter`→`JwtAuthFilter`; chú thích 13→14 module
    - `.claude/commands/new-service.md`: scaffold `JwtAuthFilter` re-validate (thay header-trust) + thêm dep `spring-boot-starter-security` + `common-security`
    - `README.md`: section 8 JWT → defense-in-depth (re-validate mỗi service, bỏ header identity, thêm RequestRateLimiter); bảng module thêm dòng `common-security`
    - Per-service rate-limit (`rate_limit:join/proof/review/register`) GIỮ NGUYÊN — đó là limit nội bộ service, khác global limit ở gateway

### 🔄 Đang làm
- Còn cần điền `.env`: `GOOGLE_CLIENT_ID/SECRET`, `SENDGRID_API_KEY`, `CLOUDINARY_*`, `OPENAI_API_KEY`, `FCM_SERVER_KEY`

### 📋 Việc tiếp theo (theo thứ tự ưu tiên)
1. **Day 4** — `user-service` auth core (register, email verify, login, refresh token, logout)
   - ⚠️ Token user-service phát hành PHẢI khớp claim contract của `JwtUtil`: `sub`=userId(UUID), `roles`=`List<String>` (vd `["ROLE_USER"]`), `jti`=UUID — nếu không gateway đọc sai
   - Build `jwtAuthFilter` per-service (re-validate token bằng `common-security` `JwtUtil`) theo `rbac-security.md` mới
2. **Hoàn thiện `.env`** — điền credentials thật: Google OAuth2, SendGrid, Cloudinary, OpenAI, FCM
3. **Day 5** — `user-service` OAuth2 Google + profile endpoints + `court-service` scaffold
4. **Day 6+** — theo IMPLEMENTATION_GUIDE.md (xem Parallelism hints cho Day 8‖9, 13‖14)

### 🧠 Quyết định kỹ thuật đã chốt
- Payment: Bank QR + STAFF manual confirm — **không dùng VNPay hay bất kỳ payment gateway nào**
- Service discovery: Spring Cloud Netflix Eureka, tất cả route qua `lb://` trong Gateway
- Cross-service refs: UUID only, không có FK constraint cross-database
- Outbox Pattern: chỉ dùng trong `matchmaking-service`
- Idempotency guard (`processed_events`): `booking-service` và `escrow-service`
- `postgres-user` chạy port **5441** (không phải 5432) vì local PostgreSQL chiếm 5432
- `notification-service` + `ai-service` exclude JPA DataSource autoconfiguration (không dùng PostgreSQL)
- **JWT**: HS256 — `JWT_SECRET` trong `.env`, TTL access=15min / refresh=30 days
- **External services**: Google OAuth2, SendGrid (email), Cloudinary (proof upload), OpenAI (ai-service), FCM (push)
- **spring-dotenv**: `me.paulschwarz:spring-dotenv:3.0.0` trong parent pom → tự load `.env` khi `mvn spring-boot:run`
- **Env pattern**: infra vars có `:default` (hoạt động không cần .env), secrets không có default (fail fast nếu thiếu)
- **docker-compose chỉ chứa infra** — eureka-server và tất cả services chạy local bằng `mvn spring-boot:run`, không containerize
- **IMPLEMENTATION_GUIDE.md per-day upgrade strategy**: chỉ nâng cấp 7 ngày phức tạp (Day 4, 7, 8, 11, 12, 14, 15), giữ nguyên ngày scaffold đơn giản; nội dung upgrade là **Claude Code workflow** (agent team + slash command + worktree), không phải production tasks
- **Custom commands** (`.claude/commands/`, 10 file): custom = prompt template chạy trong **session chính** (chung context); gõ bằng `/`
- **Custom agents** (`.claude/agents/`): `spring-builder` + `test-writer` = `general-purpose` đóng gói sẵn convention (`model: inherit`). Subagent khởi động **NGUỘI** — context = prompt spawn + CLAUDE.md/rules + file tự đọc trên đĩa; **"import Agent X" = đọc file đã COMMIT**; subagent chỉ spawn khi **user yêu cầu rõ** (không tự động)
- **KHÔNG tạo `.claude/settings.json`** — hook (auto-compile + guard chặn VNPay), permission allowlist, MCP Postgres để **opt-in**; đã ghi hướng dẫn trong guide, bật bằng `/update-config`
- **Pro Workflow = Công thức Agent Team 4 bước**: `/plan-feat` → Phase nền tuần tự (commit) → N subagent song song (độc lập) HOẶC TDD (`test-writer` trước → `spring-builder` impl) → `/code-review` + `/done-check`. Song song chỉ khi độc lập; phụ thuộc → tuần tự + commit giữa
- **Kafka UI**: `provectuslabs/kafka-ui:latest` port 8080, dùng listener nội bộ `kafka:29092` — KHÔNG dùng host listener `localhost:9092` trong docker-compose nội bộ
- **Auth boundary = defense-in-depth (ĐỔI so với rule cũ)**: gateway verify JWT + downstream service **cũng re-validate** JWT. Token là **nguồn danh tính DUY NHẤT** — gateway forward **CHỈ** `Authorization: Bearer`, **KHÔNG** gửi `X-User-Id`/`X-User-Roles` (dư thừa + spoof được). `rbac-security.md` đã sửa theo.
- **`common-security` module**: class JWT dùng chung (`JwtUtil`), **web/JPA-free** để cả reactive gateway lẫn MVC service import được. Gateway **KHÔNG** depend `common` (kéo `@RestControllerAdvice` + JPA xung đột Netty/WebFlux) → gateway tự build error JSON `{code,message,timestamp}` bằng Jackson + DataBuffer.
- **JWT lib**: `io.jsonwebtoken:jjwt:0.12.6` (api compile, impl+jackson runtime) trong parent dependencyManagement. HS256, secret bytes = UTF-8 của `JWT_SECRET`.
- **Rate limit = built-in `RequestRateLimiter`** (token-bucket qua `RedisRateLimiter`, KHÔNG phải custom INCR). Bean `RedisRateLimiter(replenishRate=2, burstCapacity=100, requestedTokens=1)` ≈ "~100/min"; `KeyResolver` key=userId (fallback IP cho path public). Đánh đổi đã chấp nhận: semantics token-bucket ≠ fixed-window, **429 body rỗng**, key Redis là `request_rate_limiter.*` (KHÔNG phải `rate_limit:{userId}`).
- **Blacklist fail-open**: Redis chết khi check `session:blacklist:{jti}` → log warn + cho qua (token đã hợp lệ mật mã; gateway phải sống).
- **`spring-boot:run` workingDirectory** (fix toàn dự án): pin `${session.executionRootDirectory}` trong parent pom pluginManagement → mọi module chạy với CWD=root để spring-dotenv load đúng `.env` root (trước đó fork với CWD=module dir → không thấy `.env`). **Bắt buộc chạy `mvn` từ thư mục root.**

### 💬 Claude đã làm trong phiên này
Phiên 2026-06-08 (Day 3): hoàn thành **`api-gateway` JWT auth + rate limit**, verified end-to-end 7/7. Tạo module mới **`common-security`** (`JwtUtil` web/JPA-free, jjwt 0.12.6) để gateway + downstream dùng chung; gateway gồm `JwtAuthenticationFilter` (GlobalFilter, skip public, check Redis blacklist fail-open, forward chỉ Bearer token) + `GatewayConfig` (built-in `RequestRateLimiter` token-bucket) + `default-filters` trong yml. Theo yêu cầu user, **đổi sang defense-in-depth**: downstream re-validate JWT, gateway KHÔNG gửi `X-User-Id`/`X-User-Roles` (token là nguồn danh tính duy nhất) — đã sửa `rbac-security.md` + `redis-patterns.md`. Phát hiện & fix blocker toàn dự án: `spring-boot:run` không load `.env` root do CWD=module dir → pin `workingDirectory=${session.executionRootDirectory}` ở parent pom. Quyết định chốt qua hỏi user: dùng thư viện rate-limit built-in (chấp nhận 429 body rỗng) + service phía sau tự re-validate. Cuối phiên: giải thích diff + luồng request từng testcase cho user, rồi **đồng bộ toàn bộ docs** theo kiến trúc mới — sửa `IMPLEMENTATION_GUIDE.md` (Day 3 ✅ + Day 4 + đổi `GatewayHeaderAuthFilter`→`JwtAuthFilter`), `.claude/commands/new-service.md`, `README.md`; quét sạch không còn term cũ (single auth boundary / X-User-Id forward / RATE_LIMIT_EXCEEDED).

Phiên 2026-06-08: thêm **Kafka UI** vào `docker-compose.yml`. Dùng image `provectuslabs/kafka-ui:latest`, port 8080, kết nối Kafka qua listener nội bộ `kafka:29092`, depends on `kafka` service_healthy. Mục đích: browse topic, produce/consume message khi dev, theo dõi consumer group lag và DLT topic trực quan thay vì dùng CLI. Phiên ngắn — 1 thay đổi nhỏ, không có quyết định kiến trúc mới.

Phiên 2026-06-08: nâng dự án lên **Pro Claude Code workflow**. Tạo 5 custom command mới + tối ưu 4 command cũ thành project-aware (10 command tổng); tạo 2 custom agent `spring-builder` + `test-writer`; viết lại `IMPLEMENTATION_GUIDE.md` cho **thực thi được** — thêm "Claude Code Toolbox", viết lại "Agent Team — Công thức thực thi" (4 bước bấm-theo-được), chuẩn hoá 7 block Pro Workflow với câu gõ cụ thể. Phần lớn phiên là giải đáp + làm rõ cơ chế cho user: `/plan-feat` ≠ Plan Mode; subagent khởi động nguội, "import = đọc file đã commit từ đĩa", chỉ spawn khi yêu cầu rõ; agents/ rỗng thì vô tác dụng. Quyết định: KHÔNG tạo settings.json (giữ opt-in). Đã lưu memory về cách làm việc user ưa thích (thực thi được, ghét mô tả mơ hồ).

---

## Quick Reference

```bash
docker compose up -d                        # start all infra (PG×9, Redis, Mongo, Kafka, Zookeeper, Zipkin, ES, Kafka UI)
docker compose up -d kafka-ui               # start Kafka UI only (depends on kafka healthy)
mvn clean install -DskipTests               # build all modules
mvn -pl user-service spring-boot:run        # run one service
cd frontend && npm install && npm run dev   # frontend dev server
```

| Service | Port |
|---|---|
| eureka-server | 8761 |
| kafka-ui | 8080 |
| api-gateway | 3000 |
| user-service | 3001 |
| court-service | 3002 |
| booking-service | 3003 |
| matchmaking-service | 3004 |
| coach-service | 3005 |
| payment-service | 3006 |
| escrow-service | 3007 |
| notification-service | 3008 |
| event-service | 3009 |
| ai-service | 3010 |
