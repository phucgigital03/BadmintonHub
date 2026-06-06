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

**Cập nhật lần cuối**: 2026-06-06

### ✅ Đã hoàn thành
- Setup CLAUDE.md với rules index và quick reference
- Tạo 10 rule files trong `.claude/rules/` (architecture, payment, java-spring, database, kafka-patterns, redis-patterns, eureka-config, rbac-security, resilience, frontend)
- Tạo `IMPLEMENTATION_GUIDE.md` — kế hoạch 4 tuần từ scratch đến complete
- Tạo `.claude/commands/handoff.md` — slash command để bàn giao phiên

### 🔄 Đang làm
- _(chưa bắt đầu implement — chuẩn bị bắt đầu Week 1)_

### 📋 Việc tiếp theo (theo thứ tự ưu tiên)
1. **Day 1** — Tạo parent `pom.xml` + 13 module scaffold + `docker-compose.yml`
2. **Day 2** — `common` module (BaseAuditEntity, ErrorResponse) + `eureka-server`
3. **Day 3** — `api-gateway` (JWT filter, rate limit, lb:// routes)
4. **Day 4** — `user-service` auth core (register, email verify, login, JWT)
5. **Day 5** — `user-service` OAuth2 + profile + `court-service` scaffold

### 🧠 Quyết định kỹ thuật đã chốt
- Payment: Bank QR + STAFF manual confirm — **không dùng VNPay hay bất kỳ payment gateway nào**
- Service discovery: Spring Cloud Netflix Eureka, tất cả route qua `lb://` trong Gateway
- Cross-service refs: UUID only, không có FK constraint cross-database
- Outbox Pattern: chỉ dùng trong `matchmaking-service`
- Idempotency guard (`processed_events`): `booking-service` và `escrow-service`

### 💬 Claude đã làm trong phiên này
Phiên setup ban đầu: đọc toàn bộ .md files của project, thêm eureka-server vào kiến trúc, tạo CLAUDE.md (index rules), tạo 10 rule files theo Claude Code spec, tạo IMPLEMENTATION_GUIDE.md 4 tuần, setup handoff workflow.

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
