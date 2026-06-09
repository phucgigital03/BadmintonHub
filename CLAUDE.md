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
| `testing.md` | `**/*Test.java`, `**/*IT.java` | Test pyramid · `common-test` base (Testcontainers) · `*Test`/`*IT` · `JwtTestTokens` · `mvn verify` |
| `frontend.md` | `frontend/src/**/*.{ts,tsx}` | React Query · Zustand · axiosClient · Socket.io · forms · routing |

---

## Session Progress

> Phần này được cập nhật tự động bằng lệnh `/handoff` cuối mỗi phiên làm việc.

**Cập nhật lần cuối**: 2026-06-09 (ERD redesign + đồng bộ TOÀN BỘ docs theo ERD mới: database.md · redis-patterns.md · IMPLEMENTATION_GUIDE.md · 3 UC_*.md viết mới — 8 file docs chưa commit)

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
  - **Fix bất nhất scaffold service trong guide** (user bắt lỗi: Day 5 inline scaffold court-service, KHÔNG đi qua `/new-service` như tôi nói): align Day 5 dùng `/new-service court-service` (rồi thêm entity skeleton); làm rõ "Vòng đời 1 service" bước 3 = `/new-service` đã gồm `SecurityFilterChain` + `JwtAuthFilter` re-validate; Day 7/8/9/11/13/14/20 im lặng scaffold là ĐÚNG convention (không sửa)
- **Test Foundation HOÀN THÀNH — nền viết test scope developer (trước Day 4, verified container thật)**:
  - **Module mới `common-test`** (→ **15 module**), thêm vào parent pom (modules + dependencyManagement):
    - `AbstractIntegrationTest` — Testcontainers PostgreSQL + Redis **singleton** (start 1 lần, reuse mọi test class), `@SpringBootTest` + `@DynamicPropertySource` tự wire
    - `AbstractKafkaIntegrationTest` — extends bản trên + Kafka container (chỉ service dùng Kafka mới extends)
    - `JwtTestTokens.bearer(secret,userId,roles...)` — mint JWT test khớp claim `JwtUtil` (`CLAIM_ROLES`)
    - `ContainersSmokeIT` — verify nền: `mvn -pl common-test verify` → spin postgres:15 + redis:7 thật → 1 passed ✓
  - **Parent pom**: `maven-failsafe-plugin` (build/plugins, inherit) → `*Test.java`=unit (surefire/`mvn test`), `*IT.java`=integration (failsafe/`mvn verify`); version Testcontainers do Spring Boot BOM quản
  - **Rule mới `.claude/rules/testing.md`** (playbook test của dev) + thêm dòng vào Rules Index trong CLAUDE.md; `java-spring.md` mục Testing rút gọn trỏ sang
  - **Nâng cấp `/write-tests`** = skill gõ tay user muốn (build xong → gõ → tự viết unit+integration trên nền `common-test` + chạy `mvn verify` + fix); đồng bộ `/race-test` + agent `test-writer` (extends base class, `*IT`, `JwtTestTokens`)
  - **Guide**: thêm mục "Test Foundation (trước Day 4)" + Day 4 viết test theo nền
  - **Verify**: `mvn clean install -DskipTests` → 15/15 module BUILD SUCCESS
- **Day 4 HOÀN THÀNH — `user-service` auth core (Agent Team / Pro Workflow, build SUCCESS, CHƯA có test)**:
  - Quy trình: `/plan-feat` → duyệt → **3 phase `spring-builder` tuần tự, commit giữa mỗi phase** (dependency-aware, không song song vì các layer phụ thuộc nhau)
  - **Phase 1** (commit `95b93fd`): pom deps (`common-security` + `spring-boot-starter-validation` + `common-test` test-scope + `com.sendgrid:sendgrid-java:4.10.2` pin) · enums `AuthProvider`/`RoleName` · entities `User` (soft-delete `@Where`, `@ManyToMany` `user_roles` EAGER) / `Role` / `AuditLog` · `UserRepository`/`RoleRepository`
  - **Phase 2** (commit `7e7f272`): DTOs (record) · `JwtTokenProvider` (token **ISSUER** HS256 — `JwtUtil` chỉ validate; derive key giống hệt) · `EmailService` (SendGrid hoặc log link khi thiếu key) · `AuthService` (register/verifyEmail/login/refresh/logout/isEmailVerified) · `SecurityConfig` beans (`BCryptPasswordEncoder(12)` + `JwtUtil`)
  - **Phase 3** (commit `a890e8e`): `JwtAuthFilter` (`OncePerRequest`, re-validate Bearer, token-only identity, KHÔNG trust header) · `SecurityConfig` filter chain (stateless, `/api/auth/**` + `/actuator/health|info` permitAll, `@EnableMethodSecurity`) · `AuthController` (5 endpoint, refresh-token = cookie HttpOnly SameSite=Strict path `/api/auth`, rotation, logout idempotent + blacklist jti)
  - **Refresh token**: opaque dạng `userId:UUID`, lưu **BCrypt hash** ở `users.refresh_token_hash`, rotate mỗi lần refresh; Redis fail-open cho rate-limit register + verify token + blacklist
  - **Verify**: `mvn -pl user-service -am clean install -DskipTests` → EXIT=0 (5 module compile + package)
  - **⚠️ CHƯA làm** (theo yêu cầu user, bỏ khỏi plan): test (`/write-tests user-service auth`), smoke end-to-end qua gateway, `/code-review` + `/security-review`
- **Convention service interface + `impl/` (áp toàn dự án) — refactor user-service + sửa docs (commit `f76c37b`, build SUCCESS)**:
  - Theo style user gửi qua ảnh: `service/{Name}Service.java` = **interface** + `service/impl/{Name}ServiceImpl.java` = `@Service` impl. Subfolder **chữ thường `impl`** (chuẩn Java — user chốt, khác ảnh ở chữ I hoa)
  - **Refactor user-service**: `AuthService`/`EmailService` → interface; `AuthServiceImpl`/`EmailServiceImpl` ở `service/impl/`. `AuthServiceImpl` đánh `@Service("authService")` để **giữ tên bean** cho SpEL `@authService.isEmailVerified` (rule #10). Nested records `LoginResult`/`RefreshResult` ở lại interface → `AuthController` KHÔNG phải sửa
  - **Sửa docs cho future service tự theo**: `java-spring.md` (Package Structure + Service Layer: quy ước interface/impl + caveat đặt tên bean + ví dụ), `.claude/agents/spring-builder.md`, `.claude/commands/new-service.md`
- **`IMPLEMENTATION_GUIDE.md` — Day 4 đồng bộ current state + gom TOÀN BỘ frontend vào Day 4 (docs-only, CHƯA commit)**:
  - **Day 4 backend đánh dấu ✅** + viết lại khớp thực tế: entity `User/Role/AuditLog` + `@ManyToMany user_roles` (bỏ `UserRole` entity), **ddl-auto KHÔNG Flyway**, service interface+impl (`@Service("authService")`), `JwtTokenProvider` issuer, refresh token opaque `userId:UUID`, email log-link fallback, pom deps, 3 phase tuần tự (commit `95b93fd`/`7e7f272`/`a890e8e`), test+review HOÃN
  - **Mục con mới "🖥️ Frontend — toàn bộ app" trong Day 4**: gom scaffold (Vite/React/TS/Tailwind) + axiosClient + authStore + auth pages + courts/booking grid + matchmaking/realtime + admin/coach + events (trước đây tách Day 16–19 + EventsPage Day 20). ⚠️ Rule degradation: API chưa có → React Query `isLoading/isError` → KHÔNG crash
  - **Fallback mock data (thêm sau theo yêu cầu user)**: query lỗi → hiện **2–3 mock item** (đúng shape response) + banner "Dữ liệu mẫu" + toast; mock gom ở `src/api/mockData.ts`, CHỈ dùng trong nhánh `isError` (`query.isError ? mock : query.data`); **mutation KHÔNG mock**; data thật override khi backend xong
  - **Day 16–19 gộp thành 1 block "(Để trống)"** trỏ về Day 4; **Day 20 bỏ PHẦN C** (EventsPage → Day 4), còn event-service + ai stub + smoke; bảng Week 4 + parallelism hints + "Ví dụ thật Day 4" (section Agent Team) sửa khớp (3 phase tuần tự, không còn `Flyway`/`UserRole`/`2 subagent song song` ở Day 4)
- **FRONTEND HOÀN THÀNH — toàn bộ app (alobo.vn style), 5 phase + 7 commit, mỗi commit `npm run build` xanh**:
  - **Stack**: Vite + React 19 + TS + **Tailwind v4** (`@tailwindcss/vite`, theme tokens brand green/gold) · React Router v7 · React Query v5 · Zustand · axios · RHF + Zod v4 · react-hot-toast · react-i18next (vi/en) · react-leaflet · qrcode.react · socket.io-client. (Versions mới hơn frontend.md mặc định nhưng tương thích.)
  - **Phase 0** (`b2a23fc`): scaffold + design system — `axiosClient` (Bearer + silent refresh 401 cookie-based), `authStore` (Zustand persist), `mockData.ts`, i18n, UI kit (Button/Card/Pill/Modal/Input/PhoneInput/MockBanner/EmptyState/Spinner + AppHeader/PageShell), HomePage + NotFound
  - **Phase 1** (`844222d`): **auth CHẠY THẬT với gateway :3000** — Login/Register/VerifyEmail (RHF+Zod) → `/api/auth/*`, `ProtectedRoute`/`RoleGuard`, `GoogleButton` (disabled tới khi cấu hình OAuth2), Dashboard
  - **Phase 2** (`69eed77`): booking flow — CourtsPage (search + react-leaflet map) · `BookingTypeModal` · `SlotGrid` (legend Trống/Đã đặt/Khoá/Sự kiện = slotColors frontend.md, 5h–22h, chọn ô, zoom, bottom bar Tổng giờ/Tổng tiền/TIẾP THEO) · PriceTablePage · BookingConfirmPage → build `PaymentInfo`. `bookingStore`
  - **Phase 3** (`f92b6bf`): EventsPage (card đúng ảnh: #id [Xé vé]-type, time/courts, pill sport/skill/slot/giá) · EventDetailPage · **PaymentScreen** (bank card + QR qrcode.react + alert chuyển khoản + countdown `useCountdown` + upload proof + card Thông tin lịch đặt + XÁC NHẬN ĐẶT) theo PaymentScreenProps + payment.md
  - **Phase 4** (`84d83a5`): MatchesPage + `CreateMatchModal` (Zod chẵn 2..16, future) → MATCH_HOST · MatchDetailPage + `useMatchSocket` (slot-updated) → MATCH_PLAYER · CoachesPage/CoachDetailPage → COACH_ENROLLMENT · `AdminPage` (RoleGuard STAFF/ADMIN, 4 tab proof/matches/bookings/refund) · `notificationStore` + `NotificationBell`
  - **Fix UI** (`807e75a`): Modal đè trên Leaflet (z-`[2000]` vì Leaflet z-index ~1000); (`766a36f`): nút back-về-home trên Login/Register (AuthShell)
  - **Quy ước thật vs mock**: chỉ auth nối backend `:3000`; page khác → React Query `isError` → 2–3 mock item từ `mockData.ts` + banner "Dữ liệu mẫu", KHÔNG crash; mutation (đặt/mua/thanh toán) demo bằng toast, KHÔNG mock
  - **Verify = `npm run build` (tsc -b + vite) xanh mọi phase**; CHƯA chạy dev server runtime để click thử
- **Phiên 2026-06-09 — ERD redesign (model court/booking) + đồng bộ rule docs, KHÔNG đụng code**:
  - **`ERD_All_Services.md` refactor lớn** — sửa 3 thiết kế sai của luồng "đặt lịch trực quan":
    - **court_db tách venue/sân + giá**: `clubs` (MỚI — venue/CLB: name/address/district/geo/rating chuyển từ `courts` cũ) · `courts` đổi nghĩa = **1 sân vật lý** (`club_id`, `court_number` "Sân N", `sport`, `type`) · `court_pricing_rules` (MỚI — giá đa chiều club+sport × WEEKDAY/WEEKEND × khung giờ × FIXED/WALK_IN, UNIQUE) · `time_slots` (court_id=sân) · `court_reviews` → **`club_reviews`**
    - **booking_db = header + items**: `bookings` HEADER (bỏ `slot_id`/`court_id`/`match_start_time`; thêm `club_id`, `customer_name/phone/note`, `customer_type`, `booking_date`, `total_price`=SUM, `earliest_start_time` snapshot) · `booking_items` (MỚI — 1 ô **30' nguyên tử**, `slot_id` UK 1:1, snapshot `court_name`/giờ/`price`)
    - **đồng bộ multi-slot**: `matches` bỏ `slot_id` → `match_slots` (MỚI) + thêm `club_id` · `coach_enrollments` bỏ `slot_id` → `enrollment_slots` (MỚI) + thêm `court_id` · `events.court_id` → `club_id`
    - **Mermaid**: 6 intra-FK mới + cross-link sửa; prose Services/Key Business Rules/Cancellation đồng bộ
  - **Bỏ VNPay khỏi ERD** (4 chỗ: 3 link Mermaid + bảng Services payment-service) → Bank QR · Proof Upload · STAFF Confirm · Manual Refund (rule Never-Violate #2)
  - **7 audit fix** (sau khi tôi tự rà toàn file theo yêu cầu user): 15phút→**10 phút** (khớp `redis-patterns.md`+`payment.md`) · thêm **`time_slots.enrollment_id`** (đối xứng booking/match/event) · `escrow_transactions.reference_payment_id` string→**UUID** · tiền event `price_per_ticket`/`total_paid` int→**decimal** · `matches.total_slots` `2..12`→**`2..16`** (khớp FE) · thêm cross-link **`courts↔coach_enrollments`** · **`UK(slot_id)`** trên 3 junction (chống double-hold)
  - **`.claude/rules/database.md` sync ERD**: Snapshot Fields (`court_price` từ `court_pricing_rules` — KHÔNG còn `courts.price_per_hour`; thêm `booking_items.price`/`bookings.total_price`/`earliest_start_time`) · bookings.status (header+items, huỷ nguyên đơn, cancellation theo `earliest_start_time` × `total_price`) · Index Guidelines (`club_reviews` thay `court_reviews` + `court_pricing_rules` UNIQUE + index booking_items/clubs/courts) · Entity model notes
  - **Verify**: grep sạch — 0 VNPay, 0 `court_reviews`, 0 `15m`, 0 `2|4|6|8|10|12`; `price_per_hour` chỉ trong `court_pricing_rules`; 3 `slot_id UK`; cross-link + `enrollment_id` có mặt
- **Phiên 2026-06-09 (tiếp) — viết mới 3 UC + đồng bộ guide + redis-patterns theo ERD mới, docs-only**:
  - **3 file `UC_*.md` viết mới HOÀN TOÀN** (model CLB/Sân · ô 30' · header+items · Bank QR):
    - `UC_Visual_Day_Booking.md`: entry trang CLB → grid `GET /api/clubs/:clubId/slots` (hàng Sân × cột ô 30') → `POST /api/bookings` header+items (khoá TẤT CẢ slot, rollback nguyên đơn) → Bank QR; giá tra `court_pricing_rules` snapshot; hoàn theo `earliest_start_time`
    - `UC_Event_Booking.md`: `events` ở `club_id` + `courts_involved`; giá `price_per_ticket`/`total_paid` decimal; `event:{id}:sold` INCR; thêm Kafka `event.created`→court-service set slot EVENT
    - `UC_Matchmaking.md`: **bỏ sạch VNPay** (refund + sequence + activity → Bank QR/`manual_refunds`), **sửa đoạn corrupt 4.2b** (Player rời trận), match giữ N ô qua `match_slots`, `club_id`+`court_id`, phân biệt rõ `total_slots`(người 2..16) vs `match_slots`(ô thời gian); BR-05 12→16 + BR-18/19 mới
  - **`IMPLEMENTATION_GUIDE.md` sync ERD** (đã commit trước đó `170aab1` → giờ lại sửa): Day 5 scaffold entity (Club/Court/CourtPricingRule/TimeSlot/ClubReview) · Day 6 court-service viết lại (clubs+courts+pricing, slot **30'**, geo ở CLB, endpoint `/api/clubs/...`) · Day 7 booking header+items (lock tất cả ô) · Day 8 payment `bookingId/matchId/enrollmentId` · Day 10 `club_reviews` · Day 11 Match+`MatchSlot`+`club_id`+total_slots 2..16 · Day 12 ghi chú counter người vs ô · Day 14 `EnrollmentSlot`+courtId · Day 20 event `club_id`+`courts_involved`+decimal; thêm spec ref `UC_*.md` vào Day 6/7/11/12/20
  - **`.claude/rules/redis-patterns.md` sync**: `courts:{district}:{type}:{date}` → **`clubs:{district}:{sport}`** · `lock:slot:{slotId}` làm rõ khoá-tất-cả-ô-1-tx · `match:{matchId}:slots` = counter NGƯỜI chơi (khác `match_slots`) · **MỚI `event:{eventId}:sold`** (atomic counter vé)
  - **Verify**: grep sạch 8 file docs — UC không còn VNPay/HMAC, mermaid fence cân bằng; guide không còn `court_reviews`/single-slot; redis không còn `courts:{district}`

### 🔄 Đang làm
- Còn cần điền `.env`: `GOOGLE_CLIENT_ID/SECRET`, `SENDGRID_API_KEY`, `CLOUDINARY_*`, `OPENAI_API_KEY`, `FCM_SERVER_KEY`
- **`user-service` chưa có test + chưa smoke end-to-end** — Day 4 build xong nhưng test/review bị hoãn theo yêu cầu user
- **Frontend chưa smoke runtime** — đã build xanh nhưng chưa `npm run dev` click thử end-to-end; auth chỉ chạy thật khi bật eureka+gateway+user-service
- **Chưa commit — 8 file docs** (đồng bộ ERD redesign): `ERD_All_Services.md` · `.claude/rules/database.md` · `.claude/rules/redis-patterns.md` · `IMPLEMENTATION_GUIDE.md` · `UC_Visual_Day_Booking.md` · `UC_Matchmaking.md` · `UC_Event_Booking.md` · `CLAUDE.md` — đợi user quyết gộp 1 commit `docs:` (không Co-Authored-By)

### 📋 Việc tiếp theo (theo thứ tự ưu tiên)
1. **Commit 8 docs treo** — `ERD_All_Services.md` + `database.md` + `redis-patterns.md` + `IMPLEMENTATION_GUIDE.md` + 3×`UC_*.md` + `CLAUDE.md` → 1 commit `docs:` (KHÔNG Co-Authored-By trailer)
2. **Smoke frontend runtime** — `cd frontend && npm run dev` (:5173) + bật eureka/gateway/user-service → test register→verify(link từ log)→login→dashboard chạy thật; các page mock render đúng + không crash
3. **Test + verify Day 4** — `/write-tests user-service auth` (unit `AuthServiceTest` + integration `AuthControllerIT` extends `AbstractIntegrationTest`, `JwtTestTokens`, `mvn verify` xanh); rồi smoke end-to-end qua gateway. ĐÂY là service ĐẦU TIÊN có test tự động
4. **Hoàn thiện `.env`** — điền credentials thật: Google OAuth2, SendGrid, Cloudinary, OpenAI, FCM (SendGrid để trống thì email verify chỉ log link ra console — dev vẫn chạy)
5. **Day 5** — `user-service` OAuth2 Google + profile endpoints + `court-service` scaffold (qua `/new-service`). ⚠️ court-service phải implement theo **ERD mới**: `clubs`/`courts`(sân)/`court_pricing_rules`/`time_slots`/`club_reviews`
6. **Day 6+** — theo IMPLEMENTATION_GUIDE.md (xem Parallelism hints cho Day 8‖9, 13‖14). booking-service phải theo **header `bookings` + `booking_items`**

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
- **Convention scaffold service**: MỌI service mới scaffold qua `/new-service {name}` TRƯỚC (gồm `SecurityFilterChain` + `JwtAuthFilter` re-validate token qua `common-security` JwtUtil), rồi mới business logic. Các Day business-logic (7/8/9/11/13/14/20) cố ý im lặng về scaffold — dựa vào "Vòng đời 1 service" bước 3, KHÔNG lặp lại. Day 5 đã align theo convention này (trước đó là ngoại lệ inline)
- **Test scope = vai trò DEVELOPER** (user tự nhận là dev): chỉ unit (Mockito) + integration (Testcontainers, no H2) cho code mình build, `mvn verify` local trước commit. KHÔNG làm (backlog): CI GitHub Actions, JaCoCo coverage gate, e2e/QA suite, hook auto. Cơ chế lặp lại = **slash command gõ tay `/write-tests`** (KHÔNG hook auto theo event — user chốt gõ tay)
- **Nền test = module `common-test`** (web/JPA-free? KHÔNG — nó là test lib): service add `common-test` scope=test là đủ bộ (Testcontainers + base class + `JwtTestTokens`). Quy ước file: `*Test.java`=unit (surefire/`mvn test`), `*IT.java`=integration (failsafe/`mvn verify`). Container **singleton** (start 1 lần/JVM, Ryuk dọn). Version Testcontainers do Spring Boot 3.2.5 BOM quản — KHÔNG cần BOM riêng
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
- **Service layer = interface + `impl/` (convention TOÀN DỰ ÁN, áp cho 9 service)**: `service/{Name}Service.java` = interface (contract), `service/impl/{Name}ServiceImpl.java` = `@Service` impl chứa `@Transactional` + logic; controller/service khác inject **interface** (by type). Subfolder **chữ thường `impl`** (chuẩn Java — user chốt qua AskUserQuestion, dù ảnh mẫu viết `Impl` hoa). Đã ghi vào `java-spring.md` + `spring-builder.md` + `new-service.md`.
- **Caveat tên bean SpEL**: khi method được tham chiếu trong `@PreAuthorize` theo tên (vd `@authService.isEmailVerified`) → impl phải đặt tên bean rõ `@Service("authService")` (mặc định sẽ là `authServiceImpl`, làm hỏng SpEL). Áp dụng cho `AuthServiceImpl`.
- **`user-service` JWT issuer ≠ validator**: `common-security` `JwtUtil` CHỈ validate. Token-issuing là `JwtTokenProvider` (trong `user-service/security/`, một `@Component` provider — KHÔNG nằm trong `service/` nên không theo interface/impl) — derive `SecretKey` y hệt `JwtUtil` (HS256, UTF-8 của `JWT_SECRET`), set `sub`/`roles`(=`CLAIM_ROLES`)/`jti`/`exp`.
- **Refresh token (user-service)**: opaque value dạng `userId:UUID` (KHÔNG phải JWT), lưu **BCrypt hash** ở `users.refresh_token_hash`, gửi qua **cookie HttpOnly SameSite=Strict** path `/api/auth`, **rotate** mỗi lần refresh. Login/refresh trả access token JWT trong body.
- **Email dev fallback**: `EmailServiceImpl` — có `SENDGRID_API_KEY` → gửi thật; trống → `log.info` verify link ra console (dev chạy được không cần SendGrid). `sendgrid-java:4.10.2` pin version trong user-service pom (KHÔNG có trong BOM).
- **Build style Day 4 = Agent Team dependency-aware**: 3 phase `spring-builder` **tuần tự** (entities → service layer → web/security), commit giữa mỗi phase. KHÔNG song song vì các layer phụ thuộc nhau (chỉ song song khi thật sự độc lập). Test bị HOÃN theo yêu cầu user (không nằm trong plan đã duyệt).
- **Frontend gom HẾT vào Day 4 (đổi cấu trúc guide)**: toàn bộ FE (scaffold + auth + courts/booking + matchmaking/realtime + admin/coach + events) là **một mục con của Day 4** (user build tối nay), KHÔNG còn rải Week 4. **Day 16–19 để trống**, Day 20 chỉ còn event-service + ai stub + smoke (bỏ EventsPage FE). Vì backend mới có auth → quy ước **graceful degradation**: page gọi API chưa tồn tại handle `isLoading/isError`, KHÔNG crash; **fallback mock data 2–3 item** (gom ở `src/api/mockData.ts`, chỉ dùng nhánh `isError`, mutation KHÔNG mock) + banner "Dữ liệu mẫu"; build UI theo contract `frontend.md` để page tự "sáng" khi backend Day sau xong.
- **FE theme = bám sát alobo.vn**: dark-green gradient bg + nút **gold/amber** CTA full-width + pill badges (slot xanh lá, giá viền vàng, skill xanh dương) + slot grid legend khớp `slotColors` + PaymentScreen Bank-QR. Tailwind v4 `@theme` tokens: `brand-bg/panel/header/gold/accent`. Versions thực tế: React 19, RR v7, Zod v4, Tailwind v4 (mới hơn frontend.md nhưng dùng được).
- **FE state/contract**: `authStore` (Zustand persist accessToken+user), `bookingStore` (draft đặt sân), `notificationStore`. `axiosClient` = 1 instance, silent refresh 401 qua `POST /api/auth/refresh` (cookie, `_retry`, coalesce). Token claim khớp `JwtUtil` (sub/roles/jti). Mọi PaymentInfo (BOOKING/MATCH_HOST/MATCH_PLAYER/COACH_ENROLLMENT/EVENT_TICKET) đổ về `PaymentScreen` chung.
- **FE z-index**: Modal phải `z-[2000]` vì Leaflet dùng z-index nội bộ tới ~1000 (panes/controls/popup) → nếu thấp hơn map sẽ đè lên modal.
- **Commit KHÔNG Co-Authored-By trailer** (từ 2026-06-09): commit message bỏ dòng `Co-Authored-By: Claude` để GitHub chỉ hiện user. 4 commit cũ trước đó giữ nguyên. Đã lưu memory `feedback-no-coauthor-trailer`.
- **ERD model court/booking (chốt 2026-06-09, redesign trong `ERD_All_Services.md`)**: 1 **club** (CLB/venue) ──< N **courts** (Sân vật lý) ──< N **time_slots**; giá tách ra **`court_pricing_rules`** (đa chiều: club+sport × WEEKDAY/WEEKEND × khung giờ × FIXED/WALK_IN, UNIQUE) — `courts` KHÔNG còn `price_per_hour`. Đặt sân = **header `bookings` + N `booking_items`** (mỗi item = **1 ô 30' nguyên tử**, `slot_id` UK 1:1, snapshot `court_name`/giờ/`price`), 1 đơn = 1 thanh toán, huỷ nguyên đơn; mốc hoàn = `bookings.earliest_start_time` theo % × `total_price`. `matches`/`coach_enrollments` cũng multi-slot qua `match_slots`/`enrollment_slots` (`slot_id` UK). Review về **venue** → `club_reviews` (đổi từ `court_reviews`). Snapshot giá = tra `court_pricing_rules` lúc đặt/tạo match rồi freeze, KHÔNG đọc live.
- **Multi-club: HOÃN** — user chốt quản lý **1 CLB duy nhất** + **1 bank_account của user** trước; `clubs` table giữ (1 row), `bank_accounts` global. Khi cần scale: thêm `bank_accounts.club_id` (per-club QR) + `clubs.owner_id` (escrow settle theo owner) — đã thảo luận, để sau.
- **ERD audit conventions (chốt 2026-06-09)**: tiền VND = `decimal` (KHÔNG `int`); mọi ref id = `UUID`; junction giữ slot (`match_slots`/`booking_items`/`enrollment_slots`) có `UK(slot_id)` chống double-hold ở DB owner; `matches.total_slots` chẵn **2..16** (khớp FE `CreateMatchModal`); slot hold / host-payment = **10 phút** (khớp `redis-patterns.md` + `payment.md`, KHÔNG phải 15); `time_slots` mang back-ref cả 4 holder (`booking_id`/`match_id`/`event_id`/`enrollment_id`); `payments` ref event-ticket = **ngược** qua `event_tickets.payment_id` (by design, không thêm cột).
- **Chuỗi docs đồng bộ theo ERD (chốt 2026-06-09)**: `ERD_All_Services.md` = **nguồn chuẩn** → kéo theo `database.md` (snapshot/state/index), `redis-patterns.md` (key registry), `IMPLEMENTATION_GUIDE.md` (Day 5–20 entity/endpoint), 3 `UC_*.md` (spec luồng). Khi đổi ERD phải sync cả chuỗi. Endpoint quy ước: **venue/grid = `/api/clubs/**`**, sân-cụ-thể vẫn `/api/courts/**` (cả 2 → `lb://court-service`); booking = `POST /api/bookings` (header+items). Redis cache geo = `clubs:{district}:{sport}`; counter vé = `event:{eventId}:sold`.
- **Frontend dùng `/api/courts` (chưa đổi sang `/api/clubs`)**: FE đã build & commit theo route cũ; là "current state" thật của FE. Backend Day 6 (court-service) implement theo ERD mới (`/api/clubs`) → khi đó mới chỉnh FE court→club (việc tương lai, đã ghi trong guide).

### 💬 Claude đã làm trong phiên này
Phiên 2026-06-09 (tiếp — đồng bộ docs theo ERD mới, KHÔNG code): nối tiếp ERD redesign, user yêu cầu **viết mới hoàn toàn 3 use case** + **sửa guide** + **sync redis-patterns**. Viết lại `UC_Visual_Day_Booking.md` (CLB/Sân · grid ô 30' · header+items · khoá tất cả slot), `UC_Event_Booking.md` (event ở `club_id` + `courts_involved` + giá decimal + `event:{id}:sold`), `UC_Matchmaking.md` (**bỏ sạch VNPay** ở refund/sequence/activity, **sửa đoạn corrupt 4.2b**, match giữ N ô qua `match_slots`, phân biệt `total_slots` người vs `match_slots` ô, total_slots 2..16). Sửa `IMPLEMENTATION_GUIDE.md` Day 5/6/7/8/10/11/12/14/20 khớp ERD (clubs/courts/pricing, header+items, MatchSlot/EnrollmentSlot, event club_id) + thêm spec ref `UC_*.md`. Sync `redis-patterns.md` (`clubs:{district}:{sport}`, lock-tất-cả-ô, thêm `event:{eventId}:sold`). Verify grep sạch cả 8 file. **FE giữ `/api/courts`** (đã build) — đổi sang `/api/clubs` là việc tương lai khi build court-service. 8 file docs CHƯA commit (chờ user quyết gộp 1 commit `docs:`).

Phiên 2026-06-09 (ERD redesign + sync docs, KHÔNG code): qua nhiều vòng Q&A user phát hiện thiết kế DB sai cho luồng "đặt lịch trực quan" (1 booking nhiều slot/nhiều sân + 1 thanh toán; 1 slot=30' nhưng chọn block 3 ô = 10:00–11:30; bảng giá đa chiều T2-T6/T7-CN × khung giờ × Cố định/Vãng lai). `/plan-feat` → refactor **`ERD_All_Services.md`**: tách `clubs`(venue)/`courts`(sân)/**`court_pricing_rules`**; `bookings` header + **`booking_items`** (ô 30' nguyên tử); đồng bộ `match_slots`/`enrollment_slots`; `events.club_id`; `court_reviews`→`club_reviews`; cập nhật Mermaid + prose. Sau đó: **bỏ VNPay** 4 chỗ (rule #2) + **sync `.claude/rules/database.md`** (Snapshot Fields/bookings.status/Index theo ERD mới). User hỏi multi-club → tôi **hỏi 2 quyết định (bank/owner)** rồi user **chốt BỎ, giữ 1 CLB + 1 bank_account**. Cuối cùng user nhờ **tôi tự audit ERD** → tìm **2 lỗi thật + 4 minor + 1 ràng buộc** (10 vs 15 phút · thiếu `time_slots.enrollment_id` · type string/int · total_slots 12 vs 16 · thiếu cross-link · UK slot_id) → user chốt **sửa hết** → áp dụng 7 fix, verify grep pass 100%. Toàn phiên docs-only; 3 file (ERD + database.md + CLAUDE.md) CHƯA commit (`IMPLEMENTATION_GUIDE.md` đã commit từ trước).
Phiên 2026-06-09 (build TOÀN BỘ frontend): user gửi 5 ảnh datlich.alobo.vn làm style reference. Hỏi 2 quyết định (bám sát theme alobo · build core-first theo phase) rồi dựng FE từ scratch — **5 phase, commit giữa mỗi phase, `npm run build` xanh**: Phase 0 scaffold Vite/React19/TS/**Tailwind v4** + design system (axiosClient silent-refresh, authStore, mockData, i18n, UI kit) → Phase 1 auth chạy thật :3000 → Phase 2 booking flow (CourtsPage+map, BookingTypeModal, **SlotGrid** legend khớp slotColors, PriceTable, Confirm) → Phase 3 EventsPage + **PaymentScreen** Bank-QR (QR+countdown+upload proof) → Phase 4 matches+`useMatchSocket`, coaches, AdminPage, notifications. Sau đó fix 2 UI bug user báo: Modal đè dưới Leaflet (z-`[2000]`) + thêm nút back-home trên Login/Register. **Đổi quy ước commit: bỏ Co-Authored-By trailer** (đã lưu memory). FE chưa smoke runtime (mới build-verify). CLAUDE.md + IMPLEMENTATION_GUIDE.md vẫn chưa commit.

Phiên 2026-06-08 (fallback mock data cho FE Day 4): theo yêu cầu user, bổ sung mục "Frontend — toàn bộ app" trong `IMPLEMENTATION_GUIDE.md` — khi API chưa có và query lỗi, page hiện **2–3 mock item** (đúng shape, gom ở `src/api/mockData.ts`, chỉ dùng nhánh `isError`, mutation KHÔNG mock) + banner "Dữ liệu mẫu" thay vì trắng/crash. Sửa rule degradation + nhãn từng section + Định nghĩa Done. Docs-only, vẫn chưa commit.

Phiên 2026-06-08 (gom frontend vào Day 4 + đồng bộ guide): theo yêu cầu user, sửa `IMPLEMENTATION_GUIDE.md` — (1) viết lại Day 4 backend khớp **chính xác current state** (đánh ✅, bỏ `UserRole`/`Flyway`, thêm interface/impl + `JwtTokenProvider` + refresh opaque + email log fallback + 3 phase tuần tự với mã commit; sửa cả "Ví dụ thật Day 4" trong section Agent Team); (2) thêm mục con **"🖥️ Frontend — toàn bộ app"** vào Day 4 gom toàn bộ FE (scaffold + auth + courts + match/realtime + admin/coach + events) với rule graceful-degradation cho API chưa có; (3) **gộp Day 16–19 thành block "(Để trống)"**, bỏ EventsPage FE khỏi Day 20, cập nhật bảng Week 4 + parallelism hints. Hỏi 2 vòng để chốt scope (build hết FE tối nay trong Day 4, Day 16–19 trống). Docs-only, CHƯA commit (CLAUDE.md + IMPLEMENTATION_GUIDE.md đang chờ user quyết).

Phiên 2026-06-08 (Day 4 + convention interface/impl): hoàn thành **`user-service` auth core** theo Agent Team / Pro Workflow — `/plan-feat` (hỏi 4 quyết định: ddl-auto giữ nguyên · Role+UserRole entities · email log link khi thiếu key · build = Agent Team) → duyệt → **3 phase `spring-builder` tuần tự, commit giữa** (`95b93fd` entities/repo, `7e7f272` service layer, `a890e8e` web/security). Build `mvn -pl user-service -am clean install -DskipTests` → EXIT=0. Token issuer (`JwtTokenProvider`) khớp claim contract `JwtUtil`; refresh token opaque `userId:UUID` BCrypt-hash trong cookie HttpOnly, rotate; email fallback log link. **Test + review HOÃN theo yêu cầu user** (bỏ khỏi plan). Sau đó user gửi ảnh muốn **service layer = interface + `impl/`**: `/plan-feat` lại (hỏi casing → chốt `impl` thường) → refactor `AuthService`/`EmailService` thành interface + `*ServiceImpl` ở `service/impl/` (`@Service("authService")` giữ tên bean cho SpEL), controller không đổi; sửa 3 docs (`java-spring.md`, `spring-builder.md`, `new-service.md`) để future service tự theo. Commit `f76c37b`, build SUCCESS.

Phiên 2026-06-08 (Test Foundation): user (developer) muốn quy trình test chuẩn công ty. Hỏi 3 vòng để chốt scope = **vai trò dev** (unit + integration Testcontainers, no CI/coverage/hook) + cơ chế = **slash command gõ tay `/write-tests`** (không hook auto). Dựng nền NGAY (bước riêng trước Day 4): module **`common-test`** (`AbstractIntegrationTest`/`AbstractKafkaIntegrationTest` Testcontainers singleton + `JwtTestTokens`), parent pom + `maven-failsafe-plugin` (`*Test`=unit, `*IT`=integration), rule **`testing.md`**, nâng cấp `/write-tests` (viết+chạy test cho chức năng vừa build) + đồng bộ `/race-test`+`test-writer`. **Verified bằng container thật**: `mvn -pl common-test verify` spin postgres:15+redis:7 → pass; `mvn clean install -DskipTests` → 15/15 module SUCCESS. Đã lưu memory về test scope = dev.

Phiên 2026-06-08 (Day 3): hoàn thành **`api-gateway` JWT auth + rate limit**, verified end-to-end 7/7. Tạo module mới **`common-security`** (`JwtUtil` web/JPA-free, jjwt 0.12.6) để gateway + downstream dùng chung; gateway gồm `JwtAuthenticationFilter` (GlobalFilter, skip public, check Redis blacklist fail-open, forward chỉ Bearer token) + `GatewayConfig` (built-in `RequestRateLimiter` token-bucket) + `default-filters` trong yml. Theo yêu cầu user, **đổi sang defense-in-depth**: downstream re-validate JWT, gateway KHÔNG gửi `X-User-Id`/`X-User-Roles` (token là nguồn danh tính duy nhất) — đã sửa `rbac-security.md` + `redis-patterns.md`. Phát hiện & fix blocker toàn dự án: `spring-boot:run` không load `.env` root do CWD=module dir → pin `workingDirectory=${session.executionRootDirectory}` ở parent pom. Quyết định chốt qua hỏi user: dùng thư viện rate-limit built-in (chấp nhận 429 body rỗng) + service phía sau tự re-validate. Cuối phiên: giải thích diff + luồng request từng testcase cho user, rồi **đồng bộ toàn bộ docs** theo kiến trúc mới — sửa `IMPLEMENTATION_GUIDE.md` (Day 3 ✅ + Day 4 + đổi `GatewayHeaderAuthFilter`→`JwtAuthFilter`), `.claude/commands/new-service.md`, `README.md`; quét sạch không còn term cũ (single auth boundary / X-User-Id forward / RATE_LIMIT_EXCEEDED). User bắt thêm 1 lỗi: tôi nói "Day 5+ đi qua `/new-service`" nhưng Day 5 thực tế inline scaffold court-service → đã align Day 5 dùng `/new-service` + làm rõ convention "Vòng đời 1 service" bước 3 (scaffold gồm JwtAuthFilter re-validate), thống nhất với Day 7/8/9/11/13/14/20.

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
