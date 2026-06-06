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

**Cập nhật lần cuối**: 2026-06-07 14:00

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

### 🔄 Đang làm
- Còn cần điền `.env`: `GOOGLE_CLIENT_ID/SECRET`, `SENDGRID_API_KEY`, `CLOUDINARY_*`, `OPENAI_API_KEY`, `FCM_SERVER_KEY`

### 📋 Việc tiếp theo (theo thứ tự ưu tiên)
1. **Hoàn thiện `.env`** — điền credentials thật: Google OAuth2, SendGrid, Cloudinary, OpenAI, FCM
2. **Day 3** — `api-gateway` JWT filter + Redis blacklist check + RateLimitFilter
3. **Day 4** — `user-service` auth core (register, email verify, login, refresh token, logout)
4. **Day 5** — `user-service` OAuth2 Google + profile endpoints + `court-service` scaffold
5. **Day 6+** — theo IMPLEMENTATION_GUIDE.md (xem Parallelism hints cho Day 8‖9, 13‖14)

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

### 💬 Claude đã làm trong phiên này
Phiên này: thêm block `🚀 Bản nâng cấp — Pro Workflow (Claude Code)` vào 7 ngày phức tạp trong `IMPLEMENTATION_GUIDE.md` (Day 4, 7, 8, 11, 12, 14, 15), giữ nguyên 100% nội dung cũ của mỗi ngày. Mỗi block mô tả agent team split cụ thể (vai trò + thứ tự dependency), slash command nên chạy, worktree command nơi áp dụng, và test bắt buộc cho điểm dễ sai (race condition, Outbox/zombie). Theo lựa chọn của user: chỉ nâng cấp ngày phức tạp, ngày scaffold đơn giản giữ nguyên.

---

## Quick Reference

```bash
docker-compose up -d                        # start all infra (PG×9, Redis, Mongo, Kafka, Eureka, Zipkin)
mvn clean install -DskipTests               # build all modules
mvn -pl user-service spring-boot:run        # run one service
cd frontend && npm install && npm run dev   # frontend dev server
```

| Service | Port |
|---|---|
| eureka-server | 8761 |
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
