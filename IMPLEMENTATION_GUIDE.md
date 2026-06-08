# BadmintonHub — 4-Week Implementation Guide

> **Mục tiêu**: Build từ scratch đến complete trong 4 tuần với Claude Code.
> **Rules**: `.claude/rules/` — đọc trước khi bắt đầu mỗi service.
> **Nguyên tắc**: Mỗi ngày = 1 deliverable có thể chạy và test được.

---

## Cách dùng prompt trong tài liệu này

Mỗi ngày có một khối **`Prompt Claude Code`** đã viết sẵn — copy nguyên văn vào Claude Code để chạy task đó.

Quy ước trong mọi prompt:
- Luôn mở đầu bằng dòng `Đọc trước: .claude/rules/...` để Claude load đúng rule.
- Tên entity / endpoint / Redis key / Kafka topic trong prompt **khớp 100%** với registry trong rules — không tự đổi tên.
- Sau mỗi prompt, chạy phần **Định nghĩa Done** để tự kiểm tra trước khi sang ngày tiếp theo.
- Không bao giờ vi phạm 10 rule "Never Violate" trong `.claude/rules/architecture.md`.

---

## Advanced Claude Code Setup (làm 1 lần trước khi bắt đầu)

> Bốn mục dưới đây là **tùy chọn, làm 1 lần**. Custom commands (mục 1) đã tạo sẵn trong repo — dùng được ngay. Permissions / hooks / MCP (mục 2–4) cần `.claude/settings.json`; bật khi bạn thấy cần, dùng `/update-config` để Claude tự ghi file.

### 1. Custom slash commands — đã có sẵn trong `.claude/commands/`

Repo đã đóng gói sẵn các command tái sử dụng. Gõ `/` trong Claude Code để thấy danh sách:

| Command | Khi nào dùng |
|---|---|
| `/plan-feat [task]` | Bắt buộc trước mỗi service phức tạp — Claude đọc code, liệt kê file sẽ đổi, edge case, **chờ approve** rồi mới code |
| `/new-service [name]` | Scaffold 1 microservice mới đúng convention (package, Eureka client, JwtAuthFilter, application.yml) |
| `/race-test [endpoint]` | Sinh integration test concurrency (Testcontainers) cho điểm dễ race — booking slot, join match |
| `/write-tests [feature]` | Test thường (happy + edge + error), tự chạy & fix |
| `/done-check [service]` | Chạy Definition of Done: build + Eureka UP + smoke curl |
| `/kafka-trace [topic]` | Debug Kafka: consume topic, consumer lag, kiểm tra `.DLT` |
| `/redis-keys [pattern]` | Soi Redis key: lock, countdown, blacklist, rate-limit + TTL |
| `/self-review` | Tự review `git diff` như senior khó tính trước khi commit |
| `/explain-code [file]` | Hiểu code lạ + ai phụ thuộc nó trước khi sửa |
| `/handoff` | Cuối phiên — cập nhật Session Progress trong CLAUDE.md |

> Tự tạo thêm command riêng: thêm 1 file `.md` vào `.claude/commands/` với frontmatter `description` + `argument-hint` + `allowed-tools`; body dùng `$ARGUMENTS` (tham số), `!` + lệnh trong backtick (chèn output bash), `@path` (chèn file). Xem các file có sẵn làm mẫu.

### 2. Cấp quyền tự động để giảm permission prompts

Chạy `/fewer-permission-prompts` — Claude scan transcript và tự thêm allowlist vào `.claude/settings.json`. Hoặc thêm tay khối tối thiểu cho dự án này:

```jsonc
// .claude/settings.json
{
  "permissions": {
    "allow": [
      "Bash(mvn:*)",
      "Bash(docker:*)",
      "Bash(docker compose:*)",
      "Bash(git worktree:*)",
      "Bash(curl:*)"
    ]
  }
}
```

### 3. Hooks — tự động hoá đúng nghĩa (harness chạy, không phải Claude)

Hook là lệnh shell **harness tự chạy** quanh mỗi tool call → dùng cho việc lặp đi lặp lại. Dùng `/update-config` để Claude ghi vào `.claude/settings.json`.

**a. Auto-compile sau khi sửa file Java** — phát hiện lỗi compile tức thì (đọc `file_path` từ JSON stdin, lấy module = segment đầu path):

```json
{
  "hooks": {
    "PostToolUse": [{
      "matcher": "Edit|Write",
      "hooks": [{
        "type": "command",
        "command": "F=$(jq -r '.tool_input.file_path // empty'); case \"$F\" in *.java) M=${F##*badmintonHub/}; M=${M%%/*}; cd \"$CLAUDE_PROJECT_DIR\" && mvn -q -pl \"$M\" compile 2>&1 | tail -8;; esac"
      }]
    }]
  }
}
```

> ⚠️ Hook này chạy `mvn compile` **mỗi lần** sửa file `.java` — tiện khi sửa lẻ tẻ, nhưng chậm khi Claude sửa hàng loạt file liên tiếp. Bật khi debug 1 service, tắt khi scaffold nhiều file. Mặc định repo **chưa bật**.

**b. Guard hook chặn VNPay** — enforce rule #2 (payment = Bank QR, **không** payment gateway) bằng máy, không phụ thuộc trí nhớ. `PreToolUse` exit code `2` → chặn tool call:

```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "Edit|Write",
      "hooks": [{
        "type": "command",
        "command": "jq -r '.tool_input // {}' | grep -iqE 'vnpay|momo|zalopay|stripe|paypal' && { echo 'BLOCKED: rule #2 — chỉ Bank QR + STAFF confirm, không payment gateway' >&2; exit 2; } || exit 0"
      }]
    }]
  }
}
```

### 4. MCP — Postgres Inspector (Claude query DB không cần copy-paste SQL)

```json
{
  "mcpServers": {
    "postgres-user": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres", "postgresql://postgres:postgres@localhost:5441/user_db"]
    },
    "postgres-booking": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres", "postgresql://postgres:postgres@localhost:5434/booking_db"]
    }
  }
}
```

Sau khi setup: hỏi "Check booking table có PENDING record nào > 10 phút không?" — Claude tự query qua MCP thay vì `docker exec ... psql`.

---

## Tổng quan timeline

| Tuần | Trọng tâm | Output |
|---|---|---|
| **Week 1** | Foundation — infra, auth, court | eureka + gateway + user-service + court-service chạy end-to-end |
| **Week 2** | Booking core — slot, payment, escrow | Luồng đặt sân hoàn chỉnh với Bank QR + STAFF confirm |
| **Week 3** | Matchmaking + notifications + coaches | Saga join-match, Outbox, realtime slot counter, notifications |
| **Week 4** | Frontend + event-service + integration | UI hoàn chỉnh, toàn bộ luồng chạy từ browser |

---

## Claude Code Toolbox — dùng đúng công cụ cho đúng việc

Bốn nhóm năng lực của Claude Code và khi nào với tới từng cái. Đây là "bản đồ" cho toàn bộ guide — mỗi Day phía dưới chỉ là áp dụng cụ thể.

### Bảng quyết định nhanh

| Tình huống | Công cụ | Ghi chú |
|---|---|---|
| Sắp build service phức tạp | `/plan-feat` → review → approve | Không bao giờ code thẳng service lớn khi chưa chốt plan |
| Cần khảo sát "pattern này đang làm ở đâu" | Nhờ Claude dùng **subagent Explore** | Đọc rộng nhiều file, chỉ trả kết luận — không ngốn context chính |
| 2 service độc lập, làm song song | **Git worktree** + 2 session (hoặc subagent `isolation: worktree`) | Xem dependency graph bên dưới |
| 1 service to, nhiều layer/domain | Nhờ Claude **chạy nhiều subagent** theo Layer/Feature/TDD split | Trong 1 session, xem Agent Team Patterns |
| Logic dễ sai (lock, Outbox, zombie) | `/race-test` hoặc TDD split (test trước, impl sau) | Test phải fail nếu bỏ lock thì mới có giá trị |
| Xong 1 service | `/done-check` → `/code-review` → fix | Trước khi sang service kế |
| Trước Week checkpoint | `/security-review` | @PreAuthorize, secrets, JWT |
| Trước khi merge worktree về main | `/code-review ultra` | Deep multi-agent review trên cloud |
| Service không chạy / event không tới | `/kafka-trace`, `/redis-keys`, MCP Postgres | Xem Troubleshooting |
| Cuối phiên | `/handoff` | Cập nhật Session Progress trong CLAUDE.md |

### Slash commands built-in (gõ `/` để xem)

| Command | Tác dụng |
|---|---|
| `/code-review [low\|medium\|high\|max\|ultra]` | Review diff hiện tại. `low/medium` = ít finding chắc chắn; `high/max` = rộng hơn; `ultra` = multi-agent sâu trên cloud. Thêm `--fix` để tự áp findings, `--comment` để post lên PR |
| `/security-review` | Review bảo mật toàn bộ thay đổi trên branch |
| `/simplify` | Dọn code cho gọn/đỡ lặp (chỉ chất lượng, KHÔNG săn bug — bug thì dùng `/code-review`) |
| `/verify` | Chạy app thật + quan sát hành vi để xác nhận 1 thay đổi đúng |
| `/run` | Khởi động & điều khiển app để xem thay đổi chạy thực tế |
| `/fewer-permission-prompts` | Sinh allowlist permission từ transcript |
| `/update-config` | Cấu hình `.claude/settings.json` (hook, permission, env) bằng lời |

### Subagents — khi nào và loại nào

Subagent là **session phụ chạy độc lập, có context riêng**, trả về kết luận cho session chính. Bạn **yêu cầu Claude bằng lời** ("dùng 3 subagent song song để…", "dùng Explore agent tìm…") — Claude sẽ tự spawn. Mỗi subagent khởi động "nguội", phải tự dựng lại context → chỉ dùng khi việc đủ lớn để bõ.

| Loại | Dùng cho | Ví dụ trong dự án |
|---|---|---|
| **Explore** · built-in | Tìm/khảo sát read-only, fan-out nhiều file, chỉ cần kết luận | "Tìm `@PreAuthorize` email-verified đã áp ở controller nào" |
| **Plan** · built-in | Thiết kế plan triển khai, cân nhắc trade-off kiến trúc | Thiết kế Saga join-match + compensating transaction trước khi code |
| **general-purpose** · built-in | Track độc lập nhiều bước (code + search + chạy) | Track bất kỳ của Agent Team nếu chưa tạo custom agent |
| **spring-builder** · custom | Build 1 track service — nhúng sẵn 10 rule + package + Testcontainers | Mỗi track build trong Agent Team (Day 4,7,8,11,12,14,15) |
| **test-writer** · custom | Viết test Testcontainers làm "spec" TRƯỚC, KHÔNG impl | TDD split Day 11 & 15; sinh race test |

> `spring-builder` + `test-writer` (ở `.claude/agents/`) chỉ là `general-purpose` **đóng gói sẵn convention** để khỏi dặn lại mỗi lần — không bắt buộc, không thêm "sức mạnh" gì mới.

Hai tuỳ chọn quan trọng khi spawn:
- `run_in_background: true` — subagent chạy nền, bạn làm việc khác, được báo khi xong. Hợp cho test/build dài.
- `isolation: "worktree"` — subagent làm trên git worktree riêng, không đụng working tree của bạn. Hợp cho 2 service song song mà không cần tự tạo worktree tay.

### Plan mode vs `/plan-feat`

- **`/plan-feat`** = custom command: nhắc Claude đọc code, liệt kê file, edge case rồi chờ approve. Nhẹ, dùng đầu mỗi service.
- **Plan mode** (Shift+Tab để bật) = Claude **không được sửa file** cho tới khi bạn duyệt plan. Mạnh hơn — dùng khi muốn chắc chắn Claude chỉ đọc/khảo sát trong lúc lên kế hoạch cho việc rủi ro cao (matchmaking Saga, payment flow).

---

## Git Worktrees — Parallel Service Development

Mỗi git worktree là một working directory độc lập trên cùng repo — không cần switch branch, không conflict. Dùng để chạy 2 Claude Code sessions song song trên 2 service khác nhau.

### Dependency Graph — Service nào có thể làm song song

```
Week 1: SEQUENTIAL — mỗi service là nền tảng cho service sau
  Day 1 → Day 2 → Day 3 → Day 4 → Day 5

Week 2: Day 8 ‖ Day 9 (payment ‖ escrow — DB khác nhau, độc lập)
         Day 6 → Day 7 (court phải có trước booking)

Week 3: Day 11 → Day 12 (matchmaking creation → player join, sequential)
         Day 13 ‖ Day 14 (notification ‖ coach — hoàn toàn độc lập)

Week 4: Day 16 ‖ Day 17 ‖ Day 18 ‖ Day 19 (frontend pages độc lập nhau)
         Day 20 ‖ bất kỳ frontend day (event-service ‖ frontend)
```

### Cách 1 — Worktree thủ công + 2 terminal (kiểm soát rõ nhất)

```bash
# Bước 1: Tạo 2 worktree cho 2 service song song (ví dụ Day 8 ‖ Day 9)
git worktree add ../badmintonHub-payment feature/payment-service
git worktree add ../badmintonHub-escrow  feature/escrow-service

# Bước 2: COPY .env vào worktree mới — .env bị gitignore nên worktree KHÔNG tự có!
cp .env ../badmintonHub-payment/.env
cp .env ../badmintonHub-escrow/.env
cp frontend/.env ../badmintonHub-payment/frontend/.env   # nếu worktree đó đụng frontend

# Bước 3: Mở 2 terminal, mỗi terminal 1 Claude Code session
cd ../badmintonHub-payment && claude   # → paste prompt Day 8, làm payment-service (port 3006)
cd ../badmintonHub-escrow  && claude   # → paste prompt Day 9, làm escrow-service  (port 3007)

# Bước 4: Sau khi cả 2 xong + /code-review ultra pass, merge về main
cd /Users/phucnguyen/ClaudeCodeProjects/badmintonHub
git merge feature/payment-service
git merge feature/escrow-service

# Bước 5: Dọn worktrees
git worktree remove ../badmintonHub-payment
git worktree remove ../badmintonHub-escrow
git branch -d feature/payment-service feature/escrow-service
```

### Cách 2 — Subagent với `isolation: "worktree"` (không cần terminal thứ 2)

Trong 1 session, yêu cầu Claude: *"Dùng subagent với worktree isolation làm escrow-service (Day 9) ở nền, trong lúc đó mình làm payment-service ở đây"*. Claude tự tạo worktree tạm cho subagent, làm xong tự dọn nếu không có thay đổi. Hợp khi bạn không muốn quản lý worktree tay.

> **3 cảnh báo bắt buộc khi chạy song song:**
> 1. **`.env` không tự sang worktree** (gitignored) — phải `cp` thủ công, nếu không service fail fast vì thiếu secret.
> 2. **Infra dùng chung** — chỉ chạy `docker-compose up -d` MỘT lần ở repo gốc. Mọi worktree share Postgres/Redis/Kafka đó, đừng `docker compose up` lại.
> 3. **Chỉ song song service KHÁC nhau** (port khác nhau). Đừng để 2 session cùng `mvn spring-boot:run` một service → đụng port. Day 8 (3006) ‖ Day 9 (3007) an toàn; Day 11 và Day 12 cùng matchmaking-service (3004) → **KHÔNG** song song.

---

## Agent Team — Công thức thực thi

Cách **bấm-theo-được** để chia nhỏ 1 service phức tạp. Mỗi block 🚀 Pro Workflow ở từng Day phía dưới chỉ là bản rút gọn của công thức này.

### 3 sự thật phải nhớ (vì sao công thức như vậy)

1. **Subagent khởi động "nguội"** — KHÔNG thấy cuộc chat của bạn. Context của nó = *prompt bạn giao khi spawn* + *CLAUDE.md/rules tự nạp (cùng repo)* + *file nó tự `Read` trên đĩa*. ⇒ prompt giao việc phải đủ rõ.
2. **"import Agent X" = đọc file Agent X đã COMMIT từ đĩa**, không phải chia sẻ trí nhớ. ⇒ track phụ thuộc phải **commit TRƯỚC** thì track sau mới đọc được.
3. **Song song chỉ khi độc lập** — phần dính nhau (business cần entity) làm **tuần tự** trong 1 session sẽ nhanh & gọn hơn là spawn subagent nguội.

### Công thức 4 bước

```
BƯỚC 1 — PLAN:   /plan-feat {service} {mục tiêu}   → đọc plan → duyệt (hoặc sửa rồi duyệt).

BƯỚC 2 — PHASE TUẦN TỰ (phần nền, làm 1 mình, COMMIT khi xong):
   Gõ: "Dùng agent spring-builder làm {data layer}: entity + repository + migration. Xong COMMIT."
   ↳ vì sao commit? để subagent ở Bước 3 ĐỌC được code này từ đĩa (xem sự thật #2).

BƯỚC 3 — N SUBAGENT SONG SONG (các phần độc lập, cùng đọc code đã commit):
   Gõ: "Dùng 2 subagent spring-builder SONG SONG, cả hai đọc code đã commit ở Bước 2:
        • A: {track 1}
        • B: {track 2}"
   ↳ Claude spawn 2 subagent (mỗi cái context riêng) chạy đồng thời, xong gộp kết quả về.

BƯỚC 4 — CHỐT:  /code-review  →  /security-review (trước Week checkpoint)  →  /done-check {service}
```

> **Biến thể TDD** (logic dễ sai — Outbox, zombie, Saga): ở Bước 3 cho **`test-writer` chạy TRƯỚC** (viết test làm spec, để ĐỎ), rồi **`spring-builder` impl** cho xanh. Hai cái này **tuần tự** (impl phải đọc test từ đĩa), không song song.

### Ví dụ thật — Day 4 user-service (gõ nguyên văn 4 lượt)

```
1.  /plan-feat user-service auth core
    → duyệt plan.

2.  "Dùng agent spring-builder làm data layer: User/Role/UserRole/AuditLog entity
     + repository + Flyway migration. Chưa làm service/controller. Xong COMMIT."

3.  "Dùng 2 subagent spring-builder song song, cả hai đọc code vừa commit:
     • A: AuthService (register/verify/login/refresh/logout) + JWT util (HS256, jti) + SendGrid client.
     • B: AuthController + JwtAuthFilter + bean @authService.isEmailVerified + tests."

4.  /code-review  →  /security-review  →  /done-check user-service
```

### Song song hay tuần tự?

| Tình huống | Cách | Ví dụ trong guide |
|---|---|---|
| Phần B cần entity/code của phần A | **Tuần tự** (1 session, commit giữa) | payment Day 8: entity → service → controller |
| Các phần KHÔNG import nhau | **Song song** (N subagent `spring-builder`) | coach Day 14: Postgres ‖ Elasticsearch ‖ API |
| Logic dễ sai, cần "spec" trước | **TDD** (`test-writer` trước → `spring-builder` impl) | matchmaking Day 11: Outbox/zombie |

> Chưa tạo file `spring-builder`/`test-writer`? Vẫn chạy được bằng `general-purpose` built-in — chỉ là bạn phải tự dặn convention trong prompt mỗi lần. Hai agent trong `.claude/agents/` đã đóng gói sẵn lời dặn đó (xem Toolbox › Subagents).

---

## Week 1 — Foundation

### Day 1: Maven Multi-Module scaffold + infra

**Mục tiêu**: `docker-compose up` chạy được, tất cả 13 module compile. *(Module `common-security` được thêm ở Day 3 → tổng 14 module về sau.)*

Tasks:
- [ ] Tạo parent `pom.xml` (Java 21, Spring Boot 3.2.5, Spring Cloud 2023.0.1)
- [ ] Tạo 13 module rỗng: `common`, `eureka-server`, `api-gateway`, `user-service`, `court-service`, `booking-service`, `matchmaking-service`, `coach-service`, `payment-service`, `escrow-service`, `notification-service`, `event-service`, `ai-service`
- [ ] Tạo `docker-compose.yml`: PostgreSQL ×9, Redis 7, MongoDB 7, Kafka 3.6 + Zookeeper, Zipkin, Elasticsearch
- [ ] Mỗi service: `application.yml` cơ bản (port, datasource, kafka bootstrap, eureka client)
- [ ] Run: `docker-compose up -d && mvn clean install -DskipTests`

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/architecture.md và .claude/rules/eureka-config.md.

Tạo skeleton cho Maven multi-module project BadmintonHub. Yêu cầu:

1. Parent pom.xml:
   - Java 21, Spring Boot 3.2.5 (parent), Spring Cloud 2023.0.1 (dependencyManagement BOM).
   - <packaging>pom</packaging>, liệt kê đủ 13 <module>: common, eureka-server,
     api-gateway, user-service, court-service, booking-service, matchmaking-service,
     coach-service, payment-service, escrow-service, notification-service,
     event-service, ai-service.
   - Common dependencyManagement: lombok, mapstruct.

2. Mỗi module con: pom.xml tối thiểu (parent = badmintonhub), main class
   @SpringBootApplication theo package com.badmintonhub.{service}, và application.yml với:
     - server.port đúng theo bảng port trong architecture.md
     - spring.application.name = tên service (dùng cho lb://)
     - eureka client config y như eureka-config.md (trừ eureka-server)
   common không cần main class / không phải Spring Boot app, chỉ là library jar.

3. docker-compose.yml ở root, services:
   - 9 PostgreSQL 15 riêng biệt cho 9 DB (user_db, court_db, booking_db,
     matchmaking_db, payment_db, escrow_db, coach_db, event_db) + 1 cho ai nếu cần —
     theo bảng Database-per-Service trong database.md. Mỗi PG map port host khác nhau.
   - Redis 7, MongoDB 7 (notification_db), Kafka 3.6 + Zookeeper, Zipkin, Elasticsearch 8.
   - healthcheck cho từng infra service.

Đừng implement business logic — chỉ scaffold để `mvn clean install -DskipTests` pass
và `docker-compose up -d` lên đủ container.
```

**Định nghĩa Done**:
```bash
docker-compose up -d && docker-compose ps   # tất cả container healthy
mvn clean install -DskipTests               # BUILD SUCCESS, 13 modules (14 sau khi thêm common-security ở Day 3)
```

---

### Day 2: common module + eureka-server

**Mục tiêu**: `eureka-server` chạy tại http://localhost:8761.

Tasks:
- [ ] `common`: `BaseAuditEntity`, `ApiException`, `ErrorResponse`, `PageResponse<T>`
- [ ] `eureka-server`: `@EnableEurekaServer` + config
- [ ] Verify dashboard hiển thị, 0 instance

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md, .claude/rules/eureka-config.md.

PHẦN A — module common (shared library, không phải Spring Boot app):
- BaseAuditEntity: @MappedSuperclass, @EntityListeners(AuditingEntityListener.class),
  field createdAt (@CreatedDate) + updatedAt (@LastModifiedDate), cột created_at / updated_at.
- ErrorResponse: record { String code, String message, Instant timestamp } — đúng shape
  error response trong java-spring.md.
- ApiException: RuntimeException base mang field code (String) + httpStatus; thêm các subclass
  hay dùng: NotFoundException, ConflictException, UnauthorizedException, ValidationException.
- PageResponse<T>: record bọc Page<T> -> { content, page, size, totalElements, totalPages }.
- GlobalExceptionHandler dạng @RestControllerAdvice tái sử dụng được: map ApiException ->
  ErrorResponse với đúng httpStatus, map MethodArgumentNotValidException -> 400.
  (Để class này trong common, các service import lại.)

PHẦN B — module eureka-server:
- Dependency: spring-cloud-starter-netflix-eureka-server + actuator (theo eureka-config.md).
- Main class @SpringBootApplication @EnableEurekaServer.
- application.yml: port 8761, register-with-eureka:false, fetch-registry:false,
  enable-self-preservation:false (dev), wait-time-in-ms-when-sync-empty:0.

Không thêm @EnableJpaAuditing ở đây — sẽ bật ở từng service có DB.
```

**Định nghĩa Done**:
```bash
mvn -pl eureka-server spring-boot:run
# http://localhost:8761 → dashboard hiện, "Instances currently registered with Eureka" = trống
```

---

### Day 3: api-gateway ✅ ĐÃ HOÀN THÀNH

**Mục tiêu**: Gateway chạy port 3000, JWT filter hoạt động, route đến tất cả service qua `lb://`.

Tasks:
- [x] Module mới `common-security` (`JwtUtil` verify HS256 — web/JPA-free, dùng chung gateway + service)
- [x] Dependencies gateway + eureka client + redis-reactive
- [x] 10 route `lb://`
- [x] `JwtAuthenticationFilter` + Redis blacklist (fail-open) + forward **chỉ** Bearer token (KHÔNG header identity)
- [x] Rate limit bằng **built-in `RequestRateLimiter`** (token-bucket) qua `default-filters`

**Đã implement (khớp code thực tế — `JwtAuthenticationFilter` / `GatewayConfig`)**:
```
Đọc trước: .claude/rules/eureka-config.md, .claude/rules/rbac-security.md, .claude/rules/redis-patterns.md.

api-gateway = Spring Cloud Gateway, reactive WebFlux:

0. Module mới `common-security` (web/JPA-free): class `com.badmintonhub.security.JwtUtil` verify
   HS256 thuần bằng jjwt 0.12.6 — DÙNG CHUNG cho gateway + mọi downstream service. Gateway KHÔNG
   depend `common` (vì `common` kéo Spring MVC @RestControllerAdvice + JPA, xung đột Netty/WebFlux);
   gateway tự build error JSON {code,message,timestamp} bằng Jackson + DataBuffer.

1. Dependencies: spring-cloud-starter-gateway, spring-cloud-starter-netflix-eureka-client,
   spring-boot-starter-data-redis-reactive, common-security (kéo jjwt transitively).

2. application.yml: 10 route uri lb://{service-name} (đúng eureka-config.md) + Eureka client config.

3. JwtAuthenticationFilter (GlobalFilter, Ordered.HIGHEST_PRECEDENCE — chạy trước cả rate limiter):
   - Bỏ qua public path: /api/auth/**, /actuator/**.
   - Path còn lại: lấy Bearer token, verify chữ ký + hạn qua JwtUtil (HS256, secret từ env JWT_SECRET).
   - Check Redis "session:blacklist:{jti}" (redis-patterns.md) — tồn tại -> 401 (FAIL-OPEN nếu Redis chết).
   - Hợp lệ: forward NGUYÊN VẸN header `Authorization: Bearer` xuống downstream (để service re-validate);
     KHÔNG emit X-User-Id/X-User-Roles. userId chỉ stash vào exchange-attribute nội bộ để key rate-limit.
   - Token thiếu/sai/hết hạn/bị thu hồi -> 401 JSON {code,message,timestamp} ngay tại Gateway,
     codes: TOKEN_MISSING / TOKEN_EXPIRED / TOKEN_INVALID / TOKEN_REVOKED.

4. Rate limit = built-in `RequestRateLimiter` (KHÔNG custom INCR). Trong GatewayConfig:
   - @Bean RedisRateLimiter(replenishRate=2, burstCapacity=100, requestedTokens=1)  ← token-bucket ≈ "~100/min"
   - @Bean KeyResolver userKeyResolver: key = userId (từ exchange-attribute), fallback client IP cho path public.
   - application.yml `default-filters: RequestRateLimiter` (#{@redisRateLimiter} + #{@userKeyResolver}) áp cho cả 10 route.
   - Vượt -> 429 (body RỖNG — đánh đổi đã chấp nhận khi chọn limiter built-in).

Auth = DEFENSE-IN-DEPTH: gateway verify + service phía sau CŨNG re-validate JWT (rbac-security.md mới).
Token là nguồn danh tính DUY NHẤT — không truyền/không tin header identity.
```

**Định nghĩa Done** (verified 7/7):
```bash
# ⚠️ Chạy mvn TỪ THƯ MỤC ROOT — parent pom pin workingDirectory=${session.executionRootDirectory}
#    để spring-dotenv đọc .env ở root (nếu không, JWT_SECRET không resolve -> context chết).
mvn -pl api-gateway spring-boot:run
curl -i http://localhost:3000/api/courts/x   # → 503 (chưa có court-service) = route ĐÚNG
curl -i http://localhost:3000/api/bookings   # → 401 TOKEN_MISSING (thiếu JWT) = filter ĐÚNG
curl -i http://localhost:3000/actuator/health # → 200 (public, skip JWT)
# Token hợp lệ -> qua auth (503 no-instance, KHÔNG 401). Blacklist jti trong Redis -> 401 TOKEN_REVOKED.
# Bắn > burstCapacity (100) request/userId -> một số request trả 429 (body rỗng).
```

---

### Test Foundation (đã dựng — trước Day 4)

Nền test dùng chung cho mọi service từ Day 4 (xem `.claude/rules/testing.md`):
- Module **`common-test`**: `AbstractIntegrationTest` (Testcontainers PostgreSQL + Redis, singleton) ·
  `AbstractKafkaIntegrationTest` (+ Kafka) · `JwtTestTokens` (mint JWT test khớp `JwtUtil`).
- Parent pom: `maven-failsafe-plugin` → `*Test.java` = unit (surefire, `mvn test`), `*IT.java` = integration
  (failsafe, `mvn verify`). Spring Boot BOM quản version Testcontainers.
- **Cách dùng**: build chức năng → gõ **`/write-tests {service} {feature}`** (tự viết unit + integration test
  trên nền `common-test` + chạy `mvn verify` + fix). Điểm dễ race → **`/race-test {endpoint}`**.

### Day 4: user-service — Auth core

**Mục tiêu**: Register → Email verify → Login → JWT token hoạt động.

Tasks:
- [ ] Entity: `User`, `Role`, `UserRole`, `AuditLog`
- [ ] register / verify-email / login / refresh / logout
- [ ] `JwtAuthFilter` per-service: **re-validate** Bearer token bằng `common-security` `JwtUtil` (defense-in-depth)

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md,
.claude/rules/rbac-security.md, .claude/rules/redis-patterns.md.

Implement user-service auth core (DB = user_db, PostgreSQL). Bật @EnableJpaAuditing.

ENTITY (extend BaseAuditEntity, PK UUID @GeneratedValue UUID):
- User: email (unique), passwordHash (nullable cho OAuth), fullName, phone,
  isEmailVerified (default false), authProvider (enum LOCAL/GOOGLE), googleId (unique nullable),
  refreshTokenHash, fcmToken, deletedAt (soft delete + @Where("deleted_at IS NULL")).
- Role (ADMIN/STAFF/COACH/USER), UserRole join (within-service FK OK).
- AuditLog (sẽ dùng sau, tạo sẵn bảng).

ENDPOINT (base /api/auth, error shape { code, message, timestamp }):
- POST /register: validate body (@Valid), BCrypt hash password, gán ROLE_USER,
  sinh token verify -> lưu Redis "email:verify:{token}" TTL 24h (redis-patterns.md),
  gửi email qua SendGrid. Rate limit Redis "rate_limit:register:{ip}" TTL 3600s max 5.
- GET /verify-email?token=: đọc Redis token (single-use, delete sau khi đọc),
  set isEmailVerified=true.
- POST /login: verify credential -> access token JWT HS256 TTL 15m + refresh token TTL 30d
  (lưu BCrypt hash vào users.refresh_token_hash, trả về cookie HttpOnly SameSite=Strict).
  ⚠️ Claims PHẢI khớp hợp đồng `common-security` `JwtUtil`: sub=userId(UUID), roles=List<String>
  (claim name "roles" = JwtUtil.CLAIM_ROLES, vd ["ROLE_USER"]), jti=UUID — sai thì gateway đọc lệch.
- POST /refresh: đọc refresh cookie, so hash -> cấp access token mới.
- POST /logout: thêm jti vào Redis "session:blacklist:{jti}" TTL = thời gian còn lại của token.

SECURITY (rbac-security.md "Spring Security Config per service" — DEFENSE-IN-DEPTH):
- JwtAuthFilter: RE-VALIDATE Bearer token bằng common-security JwtUtil (parseAndValidate) ->
  dựng Authentication TỪ CLAIMS (sub=userId, roles). KHÔNG trust header X-User-Id/X-User-Roles
  (gateway không gửi). csrf disable, session STATELESS, /actuator/health permitAll.
- Bean @authService.isEmailVerified(authentication) cho @PreAuthorize dùng sau.

Test (testing.md): unit (Mockito) cho AuthService + integration `*IT` extends `AbstractIntegrationTest`
(`common-test`) cho register/verify/login happy + edge; endpoint secured dùng `JwtTestTokens`.
Thêm `common-test` (scope test) vào pom. Gõ `/write-tests user-service auth` → `mvn verify` xanh trước commit.
```

**Định nghĩa Done**:
```bash
curl -X POST http://localhost:3000/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'   # → 201
# verify email bằng token → login → nhận JWT → gọi endpoint authenticated qua Gateway OK
```

**🚀 Bản nâng cấp — Pro Workflow (Claude Code)**

Service phức tạp nhất Week 1 → theo **Công thức Agent Team** (§ "Agent Team — Công thức thực thi"). Gõ lần lượt:

1. **Plan** — `/plan-feat user-service auth core` → đọc plan → duyệt (chú ý edge: refresh rotation, blacklist TTL).
2. **Phase nền** (làm 1 mình, **COMMIT** khi xong) — bảo Claude: *"dùng spring-builder làm data layer: `User`/`Role`/`UserRole`/`AuditLog` entities + repository + Flyway migration; chưa làm service/controller; commit."*
3. **2 subagent song song** (cùng đọc code đã commit) — bảo Claude: *"dùng 2 subagent spring-builder song song:"*
   - **A** — `AuthService` (register/verify/login/refresh/logout) + JWT util (HS256, jti) + SendGrid client.
   - **B** — `AuthController` + `JwtAuthFilter` + bean `@authService.isEmailVerified` + tests.
4. **Chốt** — chạy:

```bash
/code-review       # fix findings
/security-review   # JWT secret env · BCrypt ≥10 · blacklist TTL = token remaining · KHÔNG log password
/done-check user-service
```

---

### Day 5: user-service — OAuth2 + Profile + court-service scaffold

**Mục tiêu**: Google login hoạt động; court-service đăng ký Eureka.

Tasks:
- [ ] Google OAuth2 + forgot/reset password
- [ ] Profile endpoints (own-resource guard)
- [ ] Cloudinary upload
- [ ] court-service: `/new-service court-service` + entity skeleton

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/rbac-security.md, .claude/rules/redis-patterns.md, .claude/rules/java-spring.md.

PHẦN A — user-service bổ sung:
- POST /api/auth/google body { idToken }: verify idToken qua Google tokeninfo endpoint,
  upsert user match theo email; user mới -> authProvider=GOOGLE, passwordHash=null,
  set googleId (unique). Cấp JWT + refresh token y như login local (rbac-security.md).
- POST /api/auth/forgot-password: sinh token -> Redis "password:reset:{token}" TTL 1h, gửi email.
- POST /api/auth/reset-password body { token, newPassword }: consume Redis token (single-use),
  cập nhật passwordHash.
- GET /api/users/{id}, PATCH /api/users/{id}: @PreAuthorize own-resource OR STAFF/ADMIN
  ("#id == authentication.principal.id or hasAnyRole('STAFF','ADMIN')").
- GET /api/users (list all): @PreAuthorize hasAnyRole('STAFF','ADMIN'), trả Page<T>.
- DELETE /api/users/{id}: @PreAuthorize hasRole('ADMIN'), SOFT delete (set deletedAt).
- POST /api/upload/image (multipart): Cloudinary upload -> trả { url }.

PHẦN B — court-service scaffold (DB = court_db):
- Scaffold qua `/new-service court-service` TRƯỚC (Vòng đời bước 3) — sinh sẵn pom, Eureka client,
  `SecurityFilterChain` + `JwtAuthFilter` re-validate (common-security JwtUtil), application.yml.
- Thêm entity skeleton (extend BaseAuditEntity, PK UUID): Court, TimeSlot, CourtReview.
  Cross-service ref ownerId/userId là UUID thuần, comment 'ref users.id · cross-service UUID',
  KHÔNG @ManyToOne cross-service (database.md).
- Chưa cần business logic — chỉ cần service start + register Eureka UP.

Verify: Eureka dashboard hiện user-service và court-service đều UP.
```

**Định nghĩa Done**:
```bash
# Google login trả JWT; forgot/reset password chạy; Eureka 4 service UP
curl http://localhost:8761  # user-service, court-service, api-gateway visible
```

---

### Week 1 Checkpoint

```bash
docker-compose up -d
mvn -pl eureka-server spring-boot:run &
mvn -pl api-gateway spring-boot:run &
mvn -pl user-service spring-boot:run &
mvn -pl court-service spring-boot:run &

curl -X POST http://localhost:3000/api/auth/register -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'
curl -X POST http://localhost:3000/api/auth/login -d '{"email":"test@test.com","password":"123456"}'
# Eureka: http://localhost:8761 → 4 services registered
```

---

## Week 2 — Booking Core

### Day 6: court-service — Courts + Slots

**Mục tiêu**: CRUD courts, slot auto-generation, geo search.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md,
.claude/rules/redis-patterns.md, .claude/rules/resilience.md, .claude/rules/rbac-security.md.

Implement đầy đủ court-service (court_db):

ENTITY: Court (name, address, district, lat, lng, type, pricePerHour, isActive, ownerId UUID
cross-service, imageUrls), TimeSlot (courtId, date, startTime, endTime, status enum
AVAILABLE/RESERVED/BLOCKED/EVENT), CourtReview (courtId, bookingId UNIQUE, userId, rating, comment).

ENDPOINT:
- POST /api/courts (@PreAuthorize hasAnyRole('STAFF','ADMIN')): tạo court + upload ảnh Cloudinary.
- GET /api/courts?lat=&lng=&radius=&district=&type=&date=: geo search (Haversine), filter,
  cache Redis "courts:{district}:{type}:{date}" TTL 60s (redis-patterns.md). Trả Page<T>.
- GET /api/courts/{id}/slots?date=: list slot theo ngày kèm status.
- GET /api/courts/{id}/slots/{slotId}: chi tiết 1 slot (cho Feign từ booking-service).
- POST /api/courts/{id}/generate-slots (STAFF): manual trigger sinh slot.
- PATCH /api/courts/slots/{id}/block (STAFF): set slot BLOCKED.

SCHEDULER (resilience.md "Slot Auto-Generation"):
- @Scheduled(cron="0 0 0 * * *"): với mọi court isActive=true, sinh slot 30 ngày tới
  (now+1 .. now+30), khung 06:00–22:00, mỗi slot 60 phút, status AVAILABLE.

INDEX (database.md): composite (court_id, date, status) trên time_slots; index ownerId.
Unique constraint court_reviews.booking_id.

Test: tạo court -> generate-slots -> query theo date thấy slot AVAILABLE.
```

**Định nghĩa Done**: tạo court → slots auto-gen → `GET /api/courts/{id}/slots?date=` trả slot AVAILABLE.

---

### Day 7: booking-service — Reservation + distributed lock

**Mục tiêu**: Book slot với Redis lock, không race condition.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/redis-patterns.md,
.claude/rules/resilience.md, .claude/rules/kafka-patterns.md, .claude/rules/database.md,
.claude/rules/rbac-security.md.

Implement booking-service (booking_db):

ENTITY: Booking (userId UUID, courtId UUID, slotId UUID — tất cả cross-service UUID,
status enum PENDING/CONFIRMED/COMPLETED/CANCELLED, amount, refundAmount).
ProcessedEvent (event_id PK varchar, processedAt) — idempotency guard (database.md).

ENDPOINT:
- POST /api/bookings (@PreAuthorize "hasAnyRole('USER','COACH') and
  @authService.isEmailVerified(authentication)" — rule #10):
  1. acquireRedisLock "lock:slot:{slotId}" TTL 5s (redis-patterns.md).
  2. Feign CourtServiceClient (lb://court-service) GET /api/courts/{courtId}/slots/{slotId}
     validate slot AVAILABLE. KHÔNG hardcode http://localhost.
  3. insert Booking PENDING, releaseRedisLock trong finally.
  Bọc acquireRedisLock bằng @CircuitBreaker(name="redis", fallbackMethod="acquireDbLock")
  (resilience.md) — fallback DB SELECT FOR UPDATE.
- PATCH /api/bookings/{id}/cancel: cancellation policy (database.md):
  >24h trước slot = refund 100%, 2–24h = 50%, <2h = 0%. Set status CANCELLED + refundAmount.
- GET /api/bookings (STAFF/ADMIN list all; user xem own) — @PreAuthorize theo rbac-security.md.

KAFKA CONSUMER (kafka-patterns.md, manual ack, idempotency guard):
- @KafkaListener topic "payment.player.confirmed" groupId "booking-service":
  check ProcessedEvent(record.key()) trước; nếu chưa -> Booking CONFIRMED, gọi court-service
  set slot RESERVED, save ProcessedEvent, ack. Lỗi -> KHÔNG ack (để retry).
- Sau confirm, publish "booking.slot.confirmed" (consumer: matchmaking, notification, escrow).
- KafkaConfig: DefaultErrorHandler exponential backoff 2s/4s/8s, 3 retries, DLT (resilience.md).

Test: 2 request đồng thời cùng slotId -> chỉ 1 thành công, 1 nhận CONFLICT.
```

**Định nghĩa Done**: 2 concurrent booking cùng slot → đúng 1 PENDING; consume `payment.player.confirmed` → CONFIRMED idempotent.

**🚀 Bản nâng cấp — Pro Workflow (Claude Code)**

booking-service = lock + Kafka, hai tầng **phụ thuộc nhau** → tuần tự (KHÔNG song song). Theo **Công thức Agent Team**:

1. **Plan** — `/plan-feat booking-service reservation + distributed lock` → duyệt.
2. **Phase nền** (**COMMIT** khi xong) — *"dùng spring-builder: `Booking` + `ProcessedEvent` entities + `BookingService` (Redis lock + Feign `lb://court-service` validate slot) + `BookingController` + cancellation policy; commit."*
3. **Phase Kafka** (đọc entity Bước 2 từ đĩa — cần entity nên KHÔNG song song) — *"dùng spring-builder: `KafkaConfig` (DLT + backoff) + consumer `payment.player.confirmed` (idempotency guard) + producer `booking.slot.confirmed`."*
4. **Test bắt buộc + chốt** — chạy:

```bash
/race-test POST /api/bookings           # 2 booking đồng thời cùng slotId → đúng 1 PENDING, 1 CONFLICT
/code-review                            # Redis lock release trong finally · @CircuitBreaker fallback DB lock · ack sau khi xử lý xong
/kafka-trace payment.player.confirmed   # consumer nhận đúng, không LAG, không rớt .DLT
/done-check booking-service
```

---

### Day 8: payment-service — Bank QR flow

> ⚡ **PARALLEL với Day 9**: payment-service và escrow-service có DB riêng, không phụ thuộc nhau khi implement. Tạo 2 worktrees và chạy 2 Claude sessions đồng thời.
> ```bash
> git worktree add ../badmintonHub-payment feature/payment-service
> git worktree add ../badmintonHub-escrow  feature/escrow-service
> ```

**Mục tiêu**: Toàn bộ payment flow PENDING → PROOF_SUBMITTED → CONFIRMED.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/payment.md (BẮT BUỘC), .claude/rules/kafka-patterns.md,
.claude/rules/redis-patterns.md, .claude/rules/resilience.md, .claude/rules/rbac-security.md.

QUAN TRỌNG: KHÔNG dùng VNPay hay bất kỳ payment gateway nào. Chỉ Bank QR + STAFF confirm thủ công.

Implement payment-service (payment_db):

ENTITY: BankAccount (bankName, accountNumber, accountName, qrImageUrl),
Payment (userId UUID, paymentType enum BOOKING/MATCH_HOST/MATCH_PLAYER/COACH_ENROLLMENT/EVENT_TICKET,
referenceId UUID — booking/match/enrollment liên quan, matchId UUID nullable, amount,
orderCode SERIAL, status enum PENDING/PROOF_SUBMITTED/CONFIRMED/EXPIRED/REFUNDED, expiresAt,
refundAmount), PaymentProof (paymentId, imageUrl, uploadedAt), ManualRefund (paymentId,
amount, toBankName, toAccountNumber, toAccountName, refundNote, executedBy).

ENDPOINT (payment.md flow):
- POST /api/payments/initiate: insert Payment PENDING, expiresAt = now+10min,
  orderCode sequential, set Redis "payment:countdown:{paymentId}" = expiresAt (ISO) TTL 10min.
  Trả { orderId, orderCode, bankName, accountNumber, accountName, qrImageUrl, amount, expiresAt }.
- POST /api/payments/{id}/proof (multipart): rate limit Redis "rate_limit:proof:{userId}" TTL 300s
  max 3; Cloudinary upload -> insert PaymentProof -> status PROOF_SUBMITTED ->
  Kafka "payment.proof.submitted" (consumer notification).
- POST /api/payments/{id}/confirm (@PreAuthorize hasAnyRole('STAFF','ADMIN')):
  status CONFIRMED -> publish "payment.host.confirmed" (type MATCH_HOST) hoặc
  "payment.player.confirmed" (type MATCH_PLAYER/BOOKING/COACH_ENROLLMENT/EVENT_TICKET).
- POST /api/payments/{id}/reject (STAFF/ADMIN): status EXPIRED ->
  "payment.player.expired" hoặc "payment.host.expired" tùy type.
- POST /api/payments/{id}/refund (STAFF/ADMIN) body { amount, toBankName, toAccountNumber,
  toAccountName, refundNote }: insert ManualRefund, status REFUNDED, refundAmount=amount,
  Kafka "payment.refund.queued" (consumer notification, payment). STAFF chuyển khoản tay.
- GET /api/payments/pending-proofs (STAFF): queue PROOF_SUBMITTED để review.
- GET /api/bank-accounts: list tài khoản nhận tiền.

SCHEDULER (resilience.md): @Scheduled(fixedDelay=60000) PENDING quá expiresAt -> EXPIRED ->
publish payment.{host|player}.expired tùy type.

KafkaConfig DLT error handler như resilience.md. Tuân state machine payments.status trong database.md.
```

**Định nghĩa Done**: initiate → proof → STAFF confirm phát đúng topic; scheduler expire PENDING quá hạn.

**🚀 Bản nâng cấp — Pro Workflow (Claude Code)**

Day 8 ‖ Day 9 trong **worktree riêng** — nhớ `cp .env ../badmintonHub-payment/.env` (gitignored!). Các tầng payment **phụ thuộc nhau** → **3 phase TUẦN TỰ trong 1 session** (KHÔNG subagent song song):

1. **Plan** — `/plan-feat payment-service Bank QR flow` → duyệt (nhấn mạnh: KHÔNG VNPay).
2. **3 phase nối tiếp** (mỗi phase 1 lượt spring-builder, commit giữa) — bảo Claude làm tuần tự:
   - **Phase 1** — `BankAccount`/`Payment`/`PaymentProof`/`ManualRefund` + repos + `orderCode` SERIAL.
   - **Phase 2** — `PaymentService` (initiate/proof/confirm/reject/refund) + Cloudinary + Redis countdown + scheduler expire.
   - **Phase 3** — `PaymentController` + Kafka producers (6 topic) + `KafkaConfig` DLT + rate-limit proof.
3. **Chốt** — chạy:

```bash
/security-review    # KHÔNG VNPay/gateway (guard hook chặn sẵn) · confirm/reject/refund STAFF-only @PreAuthorize
/done-check payment-service
/code-review ultra  # deep review trước khi merge worktree về main
```

---

### Day 9: escrow-service

> ⚡ **PARALLEL với Day 8** — xem hướng dẫn worktree ở Day 8 phía trên.

**Mục tiêu**: Escrow hold → reimburse Host → settle Court Owner.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/payment.md (Prepay + Escrow Model), .claude/rules/kafka-patterns.md,
.claude/rules/database.md, .claude/rules/rbac-security.md.

Implement escrow-service (escrow_db):

ENTITY: EscrowAccount (matchId UUID cross-service, hostId UUID, courtPrice, status enum
HOLDING/SETTLED/REFUNDED, hostReimbursed), EscrowTransaction (escrowAccountId, type enum
HOST_HOLD/PLAYER_REIMBURSEMENT/COURT_OWNER_SETTLEMENT/HOST_REFUND/PLAYER_REFUND, amount,
relatedUserId UUID, note, createdAt), ProcessedEvent (idempotency guard — database.md).

KAFKA CONSUMER (kafka-patterns.md, manual ack, LUÔN check ProcessedEvent trước — rule #5):
- "payment.host.confirmed": tạo EscrowAccount status=HOLDING, courtPrice = amount,
  log EscrowTransaction HOST_HOLD.
- "payment.player.confirmed": log PLAYER_REIMBURSEMENT, cập nhật reimburse Host theo tỉ lệ.
- "match.completed": log COURT_OWNER_SETTLEMENT -> đẩy vào STAFF manual settlement queue,
  EscrowAccount -> SETTLED.
- "match.cancelled": log HOST_REFUND + PLAYER_REFUND -> STAFF manual refund queue.
- "booking.slot.confirmed": dùng nếu cần đối soát booking (idempotent).

ENDPOINT:
- GET /api/escrow/settlements/pending (STAFF/ADMIN): queue COURT_OWNER_SETTLEMENT chờ chi.
- GET /api/escrow/refunds/pending (STAFF/ADMIN): queue refund chờ chi.
- GET /api/escrow/{matchId}: trạng thái escrow của 1 match.

Court Owner CHỈ nhận tiền khi match COMPLETED và STAFF settle tay (payment.md). KafkaConfig DLT.

Test: bắn mock các Kafka event -> kiểm tra escrow_transactions ghi đúng type & amount,
gửi trùng event_id -> không double-count (idempotency).
```

**Định nghĩa Done**: mock các event → `escrow_transactions` đúng; gửi lại cùng event_id → không nhân đôi.

---

### Day 10: integration test Week 2 + court reviews

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md (Testing), .claude/rules/database.md, .claude/rules/redis-patterns.md.

PHẦN A — court review:
- POST /api/courts/{id}/reviews (@PreAuthorize hasAnyRole('USER','COACH')):
  chỉ cho review khi có booking COMPLETED của user đó cho court này; unique 1 review / booking
  (court_reviews.booking_id UNIQUE — database.md). Cập nhật rating trung bình của court.

PHẦN B — integration test (@SpringBootTest + @Testcontainers PostgreSQL + Redis,
KHÔNG dùng H2 — java-spring.md):
Viết test mô phỏng end-to-end luồng đặt sân:
  1. STAFF tạo court -> generate slots.
  2. User (email verified) POST /api/bookings -> Payment PENDING + bank QR.
  3. POST proof -> PROOF_SUBMITTED.
  4. STAFF confirm -> Kafka payment.player.confirmed.
  5. booking-service consume -> Booking CONFIRMED, slot RESERVED.
  6. escrow ghi transaction đúng.
Assert mỗi bước + verify Kafka event propagate đúng (dùng embedded Kafka hoặc Testcontainers Kafka).
Fix mọi bug phát hiện được.
```

**Định nghĩa Done**: integration test xanh; review chỉ tạo được sau booking COMPLETED.

---

### Week 2 Checkpoint

```
User đăng ký → verify email → tìm court → xem slot → đặt slot
→ nhận bank QR + countdown → upload proof → STAFF confirm
→ booking CONFIRMED, slot blocked, escrow HOLDING
```

---

## Week 3 — Matchmaking + Notifications + Coaches

### Day 11: matchmaking-service — Match creation + Host payment

**Mục tiêu**: Host tạo match, trả tiền qua Bank QR, match → OPEN.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md (Outbox + Zombie — rule #4, #6),
.claude/rules/redis-patterns.md, .claude/rules/resilience.md, .claude/rules/database.md,
.claude/rules/rbac-security.md.

Implement matchmaking-service phần tạo match (matchmaking_db):

ENTITY: Match (hostId UUID, courtId UUID, slotId UUID — cross-service UUID,
courtPrice SNAPSHOT (rule #9 — chốt lúc tạo, immutable), pricePerPerson, totalSlots,
filledSlots, skillLevel, status enum PENDING_PAYMENT/OPEN/FULL/COMPLETED/CANCELLED),
MatchParticipant (matchId, userId UUID, role enum HOST/PLAYER, joinedAt),
OutboxEvent (topic, payload, status enum PENDING/SENT, sentAt, createdAt).

ENDPOINT:
- POST /api/matches (@PreAuthorize hasAnyRole('USER','COACH')):
  1. acquireRedisLock "lock:slot:{slotId}:match_create" TTL 10min (redis-patterns.md).
  2. snapshot courtPrice qua Feign court-service (chốt cứng vào Match).
  3. tạo Match PENDING_PAYMENT + MatchParticipant(HOST).
  4. tạo Payment record type MATCH_HOST (gọi payment-service initiate).
  KHÔNG publish Kafka trực tiếp — dùng Outbox (rule #4).

OUTBOX (kafka-patterns.md + resilience.md):
- OutboxPublisherScheduler @Scheduled(fixedDelay=3000): poll OutboxEvent status=PENDING ->
  kafkaTemplate.send -> set SENT + sentAt.

KAFKA CONSUMER (manual ack):
- "payment.host.confirmed": ZOMBIE CHECK trước (rule #6 — nếu Match CANCELLED thì publish
  compensating event "match.compensate.slot" rồi return). Nếu hợp lệ: Match -> OPEN,
  gọi court-service set slot RESERVED, Host đã ở trong participants, INCR
  Redis "match:{matchId}:slots".

SCHEDULER (resilience.md "Match Timeout"): @Scheduled(cron="0 */5 * * * *")
PENDING_PAYMENT quá 10min -> CANCELLED, delete Redis lock:slot:{slotId}:match_create,
publish "match.cancelled" (reason PAYMENT_TIMEOUT) qua Outbox.

KafkaConfig DLT. State machine matches.status theo database.md.
```

**Định nghĩa Done**: tạo match → payment MATCH_HOST → STAFF confirm → Match OPEN; timeout scheduler cancel match treo.

**🚀 Bản nâng cấp — Pro Workflow (Claude Code)**

Outbox + zombie là logic dễ sai NHẤT dự án → **biến thể TDD** (test làm "spec" trước, để Claude tự verify). Gõ lần lượt:

1. **Plan** — `/plan-feat matchmaking-service match creation + Outbox` → duyệt (Outbox rule #4 · zombie rule #6 · courtPrice snapshot rule #9).
2. **test-writer TRƯỚC** (viết test, để **ĐỎ**, **COMMIT**) — *"dùng test-writer: integration test (Testcontainers + embedded Kafka): tạo match → OutboxEvent PENDING → scheduler publish → SENT; zombie event khi match CANCELLED → publish `match.compensate.slot`; chưa impl; commit."*
3. **spring-builder impl cho XANH** (đọc test từ đĩa — tuần tự, KHÔNG song song) — *"dùng spring-builder đọc test vừa commit rồi implement cho pass: `Match`/`MatchParticipant`/`OutboxEvent` entities + `MatchService` + `OutboxPublisherScheduler` + consumer `payment.host.confirmed`."*
4. **Chốt** — chạy:

```bash
/code-review                          # rule #4 (Outbox cùng @Transactional) · rule #6 (zombie return sớm) · rule #9 (courtPrice immutable)
/kafka-trace match.compensate.slot    # zombie event có publish compensating đúng không
/done-check matchmaking-service
```

---

### Day 12: matchmaking-service — Player joins (Saga)

**Mục tiêu**: Saga join match với Redis atomic counter, compensating transactions.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md, .claude/rules/redis-patterns.md (Atomic Slot Counter),
.claude/rules/rbac-security.md.

Bổ sung matchmaking-service phần join (Saga):

ENDPOINT POST /api/matches/{id}/join (@PreAuthorize "hasAnyRole('USER','COACH') and
@authService.isEmailVerified(authentication)" — STAFF/ADMIN KHÔNG được join, rule rbac-security.md):
  1. acquireRedisLock "lock:match:{matchId}" TTL 5s.
  2. INCR "match:{matchId}:slots"; nếu > totalSlots -> DECR + throw ConflictException MATCH_FULL.
  3. rate limit Redis "rate_limit:join:{userId}" TTL 60s max 5.
  4. Trong 1 @Transactional: save MatchParticipant(PLAYER) + OutboxEvent
     (topic "match.slot.joined", consumer booking + payment).
  5. release lock trong finally.
  6. tạo Payment type MATCH_PLAYER amount = pricePerPerson.

KAFKA CONSUMER (manual ack, zombie check rule #6):
- "booking.slot.confirmed": nếu Match CANCELLED -> publish "match.compensate.slot" + return.
  Hợp lệ: filledSlots++; nếu filledSlots == totalSlots -> status FULL. Emit Socket.io.
- "payment.player.expired": COMPENSATE -> DECR "match:{matchId}:slots", remove MatchParticipant,
  filledSlots-- (idempotent, an toàn khi đến muộn).

SOCKET.IO: khi slot counter đổi (join confirm / compensate) emit "slot-updated"
{ filledSlots, totalSlots, status } tới room match:{matchId} (frontend.md useMatchSocket).

ENDPOINT GET /api/matches?status=OPEN&date=&skillLevel=: list match filter, Page<T>.

Test: nhiều request join đồng thời slot cuối -> chỉ 1 thành công (atomic counter + lock).
```

**Định nghĩa Done**: join concurrency an toàn; `payment.player.expired` compensate đúng; Socket.io emit `slot-updated`.

**🚀 Bản nâng cấp — Pro Workflow (Claude Code)**

Nối tiếp Day 11 (**cùng matchmaking-service, port 3004** — KHÔNG worktree, KHÔNG song song với Day 11). Entity `Match` đã có từ Day 11 → đi thẳng 2 subagent:

1. **Plan** — `/plan-feat matchmaking-service player join Saga` → duyệt.
2. **2 subagent song song** (đọc entity `Match` đã có trên đĩa; 2 phần độc lập) — bảo Claude: *"dùng 2 subagent spring-builder song song:"*
   - **A** — endpoint join (Redis lock `lock:match:{id}` + INCR atomic counter + rate limit) + OutboxEvent `match.slot.joined` + consumer compensate (`booking.slot.confirmed` zombie, `payment.player.expired` DECR).
   - **B** — Socket.io server (3004) + emit `slot-updated` room `match:{matchId}` + GET `/api/matches` filter Page<T>.
3. **Test bắt buộc + chốt** — chạy:

```bash
/race-test POST /api/matches/{id}/join   # join slot cuối đồng thời → đúng 1 thành công, còn lại MATCH_FULL
/code-review                # atomic counter DECR khi vượt slot · compensate idempotent · STAFF/ADMIN KHÔNG join
/redis-keys match:*:slots   # counter = filledSlots, không âm, không vượt totalSlots
```

---

### Day 13: notification-service

> ⚡ **PARALLEL với Day 14 (coach-service)**: Hai service dùng DB khác nhau và không gọi nhau. Tạo 2 worktrees để chạy song song:
> ```bash
> git worktree add ../badmintonHub-notification feature/notification-service
> git worktree add ../badmintonHub-coach        feature/coach-service
> ```

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md (DLQ — rule #7), .claude/rules/architecture.md.

Implement notification-service (notification_db = MongoDB):

DOCUMENT: NotificationTemplate (code, channel enum PUSH/EMAIL, titleTemplate, bodyTemplate),
NotificationHistory (userId UUID, templateCode, channel, payload, isRead, sentAt).

SEED templates: SLOT_JOINED, PAYMENT_CONFIRMED, MATCH_CANCELLED, EMAIL_VERIFY,
PASSWORD_RESET, BOOKING_RECEIPT, PROOF_SUBMITTED, REFUND_QUEUED.

KAFKA CONSUMER (groupId notification-service, manual ack):
- "payment.proof.submitted" -> push STAFF "có proof mới cần review".
- "payment.host.confirmed" -> push/email Host "match đã OPEN".
- "payment.player.confirmed" -> push/email Player "đã join thành công".
- "match.cancelled" -> push/email tất cả participants.
- "payment.refund.queued" -> push/email user "refund đang xử lý".
- "escrow.host.reimbursed" -> push Host.
Gửi qua SendGrid (email) + FCM (push). Lưu NotificationHistory.

DLQ (rule #7, resilience.md): KafkaConfig DefaultErrorHandler exponential backoff 2s/4s/8s,
3 retries, DeadLetterPublishingRecoverer route "{topic}.DLT". KHÔNG nuốt exception.

ENDPOINT:
- GET /api/notifications (own, Page<T>), POST /api/notifications/{id}/read,
  POST /api/notifications/read-all.
- PATCH /api/users/{id}/fcm-token: cập nhật FCM token (gọi user-service hoặc lưu local).
```

**Định nghĩa Done**: các event tạo NotificationHistory + gửi; lỗi 3 lần → vào `.DLT`.

---

### Day 14: coach-service

> ⚡ **PARALLEL với Day 13** — xem hướng dẫn worktree ở Day 13 phía trên.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md, .claude/rules/kafka-patterns.md,
.claude/rules/redis-patterns.md, .claude/rules/rbac-security.md, .claude/rules/payment.md.

Implement coach-service (coach_db PostgreSQL + Elasticsearch):

ENTITY: Coach (userId UUID, specialty, hourlyRate, bio, district, rating, status enum
PENDING_APPROVAL/ACTIVE/SUSPENDED, deletedAt soft delete + @Where), CoachSchedule
(coachId, dayOfWeek, startTime, endTime), CoachEnrollment (coachId, studentId UUID,
status enum PENDING/CONFIRMED/COMPLETED/CANCELLED, amount), CoachReview (coachId,
enrollmentId UNIQUE, studentId, rating, comment).

ELASTICSEARCH: index document Coach khi create/update; search theo specialty,
hourlyRate range, rating, district.

ENDPOINT:
- POST /api/coaches (apply, @PreAuthorize hasRole('COACH')): status PENDING_APPROVAL.
- PATCH /api/coaches/{id}/approve (@PreAuthorize hasRole('ADMIN')): -> ACTIVE.
- PATCH /api/coaches/{id}/suspend (@PreAuthorize hasAnyRole('STAFF','ADMIN')): -> SUSPENDED.
- GET /api/coaches?specialty=&minRate=&maxRate=&minRating=&district=: Elasticsearch search.
- POST /api/coaches/{id}/enroll (@PreAuthorize hasAnyRole('USER','COACH')):
  tạo CoachEnrollment PENDING + Payment type COACH_ENROLLMENT (payment.md) -> trả bank QR.
- POST /api/coaches/{id}/reviews: chỉ enrollment COMPLETED; rate limit
  Redis "rate_limit:review:{userId}" TTL 86400s max 2/ngày; unique 1 review/enrollment.

KAFKA CONSUMER: "payment.player.confirmed" (type COACH_ENROLLMENT) -> Enrollment CONFIRMED
(idempotent). KafkaConfig DLT. State machine coaches.status theo database.md.
```

**Định nghĩa Done**: apply → ADMIN approve → ACTIVE; enroll → bank QR → confirm → CONFIRMED; ES search trả đúng.

**🚀 Bản nâng cấp — Pro Workflow (Claude Code)**

Day 14 ‖ Day 13 trong **worktree riêng** — nhớ `cp .env ../badmintonHub-coach/.env` (gitignored!). Theo **Công thức Agent Team**:

1. **Plan** — `/plan-feat coach-service profiles + Elasticsearch + enrollment` → duyệt.
2. **Phase nền** (**COMMIT** khi xong) — *"dùng spring-builder: `Coach`/`CoachSchedule`/`CoachEnrollment`/`CoachReview` entities + repos + state machine + soft delete; commit."*
3. **2 subagent song song** (cùng đọc entity đã commit) — bảo Claude: *"dùng 2 subagent spring-builder song song:"*
   - **A** — Elasticsearch: index document `Coach` khi create/update + search (specialty/rate range/rating/district).
   - **B** — API + Kafka: controllers (apply/approve/suspend/enroll/review + rate-limit 2/ngày) + consumer `payment.player.confirmed` type COACH_ENROLLMENT.
4. **Chốt** — chạy:

```bash
/code-review        # ES sync với Postgres khi create/update · @PreAuthorize approve=ADMIN suspend=STAFF/ADMIN
/done-check coach-service
/code-review ultra  # trước khi merge worktree về main
```

---

### Day 15: Week 3 integration + admin endpoints

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md, .claude/rules/resilience.md (Cleanup Schedulers),
.claude/rules/rbac-security.md.

PHẦN A — match lifecycle:
- PATCH /api/matches/{id}/cancel (@PreAuthorize host OR STAFF/ADMIN): status CANCELLED ->
  publish "match.cancelled" qua Outbox (consumer notification, booking, escrow) -> escrow
  refund queue + notifications.
- PATCH /api/matches/{id}/complete (@PreAuthorize hasAnyRole('STAFF','ADMIN')): status COMPLETED
  (chỉ từ FULL/OPEN) -> publish "match.completed" qua Outbox -> escrow settlement queue.

PHẦN B — admin & cleanup:
- POST /api/admin/kafka/replay?topic={topic.DLT} (@PreAuthorize hasRole('ADMIN')):
  đọc DLT, gửi lại vào topic gốc (kafka-patterns.md replay endpoint).
- Outbox cleanup (matchmaking): @Scheduled(cron="0 0 2 * * *") delete OutboxEvent SENT > 30 ngày.
- ProcessedEvent cleanup (booking + escrow): @Scheduled(cron="0 0 3 * * *") delete > 7 ngày.

PHẦN C — integration test toàn bộ Saga join-match (Testcontainers): host tạo match -> confirm ->
OPEN -> player join -> confirm -> filledSlots++ -> FULL -> complete -> escrow settlement.
Verify zombie check (event đến sau khi CANCELLED -> compensate, không xử lý stale — rule #6).
```

**Định nghĩa Done**: cancel/complete phát đúng event; DLQ replay chạy; cleanup scheduler hoạt động; Saga test xanh.

**🚀 Bản nâng cấp — Pro Workflow (Claude Code)**

Day tích hợp — verify cả Week 3. 3 phần **độc lập** → **3 subagent song song** (trong đó 1 là test-writer):

1. **Plan** — `/plan-feat Week 3 integration + admin endpoints` → duyệt.
2. **3 subagent song song** (độc lập nhau) — bảo Claude: *"dùng 3 subagent song song:"*
   - **A** (spring-builder) — match lifecycle `cancel`/`complete` qua Outbox + admin Kafka replay (ADMIN-only).
   - **B** (spring-builder) — cleanup schedulers (outbox SENT >30 ngày, processed_events >7 ngày).
   - **C** (test-writer) — integration test full Saga join-match (host tạo → confirm → OPEN → join → FULL → complete → escrow settlement) + verify zombie check (event sau CANCELLED → compensate, không xử lý stale).
3. **Chốt** — chạy:

```bash
/kafka-trace match.cancelled    # cancel phát đúng event tới notification/booking/escrow
/security-review                # @PreAuthorize đủ · admin replay ADMIN-only · không leak cross-service data
/code-review ultra              # checkpoint cuối Week 3, deep review trước khi sang frontend
```

---

### Week 3 Checkpoint

```
Host tạo match → Bank QR → STAFF confirm → match OPEN (Socket.io broadcast)
Player join → Bank QR → STAFF confirm → slot counter INCR (Socket.io)
Match FULL → notification; STAFF complete → escrow settlement queue
Coach: enroll → Bank QR → confirm → enrollment CONFIRMED
```

---

## Week 4 — Frontend + Event Service + Polish

### Day 16: Frontend scaffold + auth pages

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md.

Scaffold frontend (Vite + React 18 + TypeScript + Tailwind) theo File Structure trong frontend.md:

- src/api/axiosClient.ts: baseURL = import.meta.env.VITE_API_URL; request interceptor gắn
  Bearer accessToken từ authStore; response interceptor silent refresh khi 401
  (POST /api/auth/refresh, cookie-based, _retry flag). MỌI call HTTP đi qua client này.
- src/store/authStore.ts (Zustand): { accessToken, user, setAuth, clearAuth }. KHÔNG để
  server data trong Zustand — dùng React Query cho data từ API.
- Pages LoginPage, RegisterPage: React Hook Form + Zod (resolver zodResolver).
- ProtectedRoute + RoleGuard components.
- src/App.tsx: React Router v6 createBrowserRouter — public + protected routes.
- i18n react-i18next: src/i18n/vi.json + en.json; mọi string qua t('key'), không hardcode.
- react-hot-toast cho feedback. QueryClientProvider bọc app.

Test: register -> verify email -> login -> redirect dashboard.
```

**Định nghĩa Done**: register/login chạy trên browser, silent refresh 401 hoạt động.

---

### Day 17: Frontend — Courts + Visual Booking Grid

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md (Slot Color Convention, Payment Screen, React Query).

- CourtsPage: search form (district, type, date) qua React Query useQuery; react-leaflet map
  với pin court.
- CourtDetailPage: thông tin court + component SlotGrid.
- SlotGrid: lưới slot theo thời gian, màu theo status dùng ĐÚNG slotColors trong frontend.md
  (AVAILABLE bg-white hover green / RESERVED bg-red-200 / BLOCKED bg-gray-300 / EVENT bg-purple-200).
- Click slot AVAILABLE -> booking modal -> useMutation POST /api/bookings ->
  invalidateQueries(['court', id]).
- PaymentScreen component theo interface PaymentScreenProps trong frontend.md (paymentId,
  orderCode, bankName, accountNumber, accountName, qrImageUrl, amount, expiresAt):
  hiện bank info + QR + countdown timer + upload zone; multipart POST /api/payments/{id}/proof;
  disable nút confirm tới khi chọn ảnh.
- toast.success / toast.error qua react-hot-toast (errors lấy err.response?.data?.message).

Test: tìm sân -> click slot -> đặt -> PaymentScreen -> upload ảnh.
```

**Định nghĩa Done**: đặt sân từ browser → PaymentScreen → upload proof OK.

---

### Day 18: Frontend — Matchmaking + Real-time

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md (Socket.io useMatchSocket, React Query, forms).

- MatchesPage: list open matches, filter date + skill level (useQuery).
- MatchDetailPage: match info + slot counter + nút join.
- src/hooks/useMatchSocket.ts: io(VITE_WS_URL) (matchmaking-service:3004), emit
  'join-match-room' matchId, listen 'slot-updated' -> queryClient.setQueryData(['match', id], ...).
  Socket CHỈ dùng qua hook này, không gọi trong component.
- Create match modal: React Hook Form + Zod (totalSlots chẵn 2..16, pricePerPerson >=0,
  date tương lai) -> submit -> PaymentScreen (type MATCH_HOST).
- Join match -> POST /api/matches/{id}/join -> PaymentScreen (amount = pricePerPerson).
- notificationStore (Zustand) + NotificationBell badge count.

Test: 2 tab cùng join 1 match -> slot counter cập nhật real-time đồng thời.
```

**Định nghĩa Done**: slot counter realtime giữa 2 tab; tạo/join match → PaymentScreen.

---

### Day 19: Frontend — Admin panel + Coach pages

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md, .claude/rules/rbac-security.md (Permission Matrix).

- AdminPage (RoleGuard roles={['STAFF','ADMIN']}):
  • Tab Proof Review: GET /api/payments/pending-proofs, xem ảnh proof, nút Confirm
    (POST /api/payments/{id}/confirm) / Reject (POST /api/payments/{id}/reject).
  • Tab Matches: list all matches + status filter, force cancel.
  • Tab Bookings: list all bookings.
  • Tab Refunds: form manual refund (toBankName, toAccountNumber, toAccountName, amount,
    refundNote) -> POST /api/payments/{id}/refund.
- CoachesPage: search (specialty, hourlyRate, rating) — Elasticsearch-powered.
- CoachDetailPage: profile + schedule + nút enroll -> PaymentScreen (type COACH_ENROLLMENT).
- DashboardPage (User): my bookings, my matches, my enrollments, my notifications.

Test: STAFF login -> thấy pending proofs -> confirm -> user nhận notification.
```

**Định nghĩa Done**: STAFF confirm proof từ UI → user nhận notification; coach search + enroll chạy.

---

### Day 20: event-service + ai-service stub + final integration

> ⚡ **PARALLEL với bất kỳ frontend day nào còn dở**: event-service backend độc lập hoàn toàn với frontend pages. Chạy 2 sessions song song nếu cần.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/database.md (event_tickets state machine), .claude/rules/payment.md,
.claude/rules/kafka-patterns.md, .claude/rules/frontend.md.

PHẦN A — event-service (event_db):
- Entity: Event (name, description, courtId UUID, startTime, endTime, ticketPrice, totalTickets,
  type enum SOCIAL/COMPETITIVE), EventTicket (eventId, userId UUID, status enum
  PENDING/CONFIRMED/CANCELLED/REFUNDED — database.md).
- POST /api/events (@PreAuthorize hasAnyRole('STAFF','ADMIN')): tạo event + block time_slots
  của court (status=EVENT qua court-service).
- POST /api/events/{id}/tickets/purchase (@PreAuthorize hasAnyRole('USER','COACH')):
  tạo Payment type EVENT_TICKET -> trả Bank QR.
- @KafkaListener "payment.player.confirmed" (type EVENT_TICKET) -> ticket CONFIRMED (idempotent).
- KafkaConfig DLT.

PHẦN B — ai-service stub:
- GET /api/ai/chat: trả static response (placeholder cho RAG — backlog).

PHẦN C — frontend EventsPage: list events, buy ticket -> PaymentScreen.

PHẦN D — end-to-end smoke test toàn bộ luồng (court booking, match host+join, coach enroll,
event ticket, STAFF admin ops). Fix critical bug.
```

**Định nghĩa Done**: mua vé event chạy end-to-end; ai stub trả response; smoke test toàn hệ thống pass.

---

### Week 4 Checkpoint — Final Checklist

```bash
docker-compose up -d
mvn clean install -DskipTests
# Start all services...

# Smoke test checklist:
□ User register + email verify + login (JWT + cookie)
□ Google OAuth2 login
□ Court search (geo + filter) + slot grid display
□ Court booking → Bank QR → upload proof → STAFF confirm → slot reserved
□ Create match (Host) → Bank QR → STAFF confirm → match OPEN
□ Join match (Player) → Bank QR → STAFF confirm → slot counter +1 (real-time)
□ Match complete → escrow settlement queue
□ Coach search (Elasticsearch) + enroll + review
□ Event ticket purchase
□ Notification push/email on key events
□ Admin panel: proof review queue + confirm/reject + manual refund
□ JWT blacklist on logout
□ Rate limiting works (429 on exceed)
□ Eureka dashboard: all services UP
□ Zipkin: distributed traces visible
```

---

## Quy trình làm việc với Claude Code

### Vòng đời 1 service (lặp lại mỗi Day)
```
1. Xem Parallelism hint của ngày → nếu ‖ thì tạo worktree (nhớ cp .env!) hoặc nhờ subagent isolation.
2. /plan-feat {service} {mục tiêu} → review plan → approve. Rủi ro cao (Saga, payment) → bật Plan mode (Shift+Tab).
3. Service mới? → /new-service {name} scaffold trước (gồm SecurityFilterChain + JwtAuthFilter
   RE-VALIDATE qua common-security JwtUtil — defense-in-depth), rồi mới vào business logic của Day.
   Các Day business-logic KHÔNG lặp lại bước scaffold — luôn /new-service trước nếu service chưa tồn tại.
4. Implement: phase tuần tự (layer phụ thuộc) HOẶC subagent song song (domain độc lập).
   Trong 1 phase: entity → repository → service → controller → test.
5. Logic dễ race (booking, join match) → /race-test {endpoint}.
6. /done-check {service} → build + Eureka UP + smoke curl.
7. /code-review → fix findings. Trước Week checkpoint thêm /security-review.
8. Kẹt? → /kafka-trace {topic} (event không tới) · /redis-keys {pattern} (lock treo).
9. /handoff cuối phiên → cập nhật Session Progress trong CLAUDE.md.
```

### Debug pattern (ưu tiên slash command, raw chỉ là fallback):
```bash
/kafka-trace payment.host.confirmed   # thay kafka-console-consumer thủ công — kèm lag + check .DLT
/redis-keys "lock:*"                  # thay redis-cli keys — kèm TTL + đối chiếu Key Registry

# Fallback raw khi muốn soi tay:
mvn -pl {service} spring-boot:run 2>&1 | grep -E "ERROR|WARN|Started"
docker exec redis redis-cli --scan --pattern "payment:countdown:*"
```

### Thứ tự ưu tiên khi bị block:
1. Đọc lại rule file liên quan
2. Kiểm tra service đã register Eureka chưa
3. Kiểm tra Kafka consumer group offset
4. Kiểm tra Redis key còn sống chưa

---

## Code Review Workflow

Tích hợp review vào mọi checkpoint để đảm bảo code quality trước khi sang tuần tiếp.

```bash
# Sau mỗi service hoàn thành (diff nhỏ, nhanh):
/code-review

# Trước mỗi Week checkpoint (check security patterns):
/security-review

# Trước khi merge worktree branch về main (deep review):
/code-review ultra

# Fix findings rồi mới merge:
git merge feature/{service}
```

### Checklist trước merge worktree

```
□ /code-review ultra pass (không có critical finding)
□ /security-review: không có hardcoded secret, JWT đúng, @PreAuthorize đủ
□ mvn clean install -DskipTests BUILD SUCCESS từ root
□ Service register Eureka UP
□ Định nghĩa Done của ngày đó pass
```

---

## Troubleshooting nhanh

> Nhanh nhất: `/kafka-trace {topic}` · `/redis-keys {pattern}` · `/done-check {service}` — Claude tự chạy & đối chiếu rule. Lệnh raw dưới đây là fallback khi muốn soi tay.

```bash
# Service không start — xem 20 dòng cuối log
mvn -pl {service} spring-boot:run 2>&1 | tail -20

# Kafka consumer không nhận message
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group {service}
# → xem LAG column, nếu LAG > 0 = message đang chờ

# Redis key TTL còn bao lâu
docker exec -it redis redis-cli TTL "payment:countdown:{id}"
docker exec -it redis redis-cli TTL "session:blacklist:{jti}"

# Postgres — query trực tiếp (hoặc dùng MCP nếu đã setup)
docker exec -it postgres-user psql -U postgres -d user_db \
  -c "SELECT id, email, is_email_verified FROM users LIMIT 5;"

# Zipkin — xem distributed trace
open http://localhost:9411

# Eureka — check service UP
curl -s http://localhost:8761/eureka/apps | grep -E "<app>|<status>"
```

---

## Scope ngoài 4 tuần (backlog)

Những item này hoàn thành sau khi core flow chạy ổn định:

- [ ] ai-service: RAG chatbot thực sự (LLM integration)
- [ ] Waiting list cho matches (khi match FULL)
- [ ] Push notifications mobile (FCM deep integration)
- [ ] `@AuditLog` aspect ghi audit_logs cho admin operations
- [ ] Production hardening: HTTPS, env secrets, Kubernetes manifests
- [ ] Load testing (Gatling/k6) cho booking race conditions
- [ ] Swagger UI đầy đủ cho tất cả services
- [ ] E2E tests (Playwright/Cypress)
