---
allowed-tools: Read, Write, Edit, Grep, Glob, Bash(mvn:*)
argument-hint: [endpoint hoặc method] (vd: POST /api/bookings)
description: Viết integration test race-condition (Testcontainers) cho concurrency
---
Đọc trước: .claude/rules/java-spring.md (Testing), .claude/rules/redis-patterns.md.

Viết integration test concurrency cho: **$ARGUMENTS**

1. `@SpringBootTest` + `@Testcontainers` với PostgreSQL + Redis THẬT (KHÔNG H2 — java-spring.md).
2. Bắn N request đồng thời (`ExecutorService` + `CountDownLatch`) vào cùng resource (cùng `slotId` / `matchId`).
3. Assert: đúng **1** request thành công, phần còn lại nhận `ConflictException` (code `CONFLICT` / `MATCH_FULL`).
4. Verify trạng thái cuối ở DB + Redis counter đúng (không over-book, counter không âm).
5. Chạy test, fix tới khi xanh. Test naming: `methodName_scenario_expectedResult`.

Đây là điểm dễ sai nhất dự án (Redis SETNX lock + atomic counter `match:{id}:slots`). Test phải FAIL nếu cố tình bỏ lock — nếu không thì test vô nghĩa, viết lại.
