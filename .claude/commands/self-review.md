---
allowed-tools: Read, Grep, Glob, Bash(git diff:*), Bash(git show:*)
description: Tự review diff khắt khe theo chuẩn BadmintonHub
---
## Thay đổi chưa commit
!`git diff HEAD`

## Commit gần nhất (xem nếu phần trên rỗng)
!`git show HEAD --stat`

Review phần trên như một senior engineer khó tính, bám .claude/rules/. Ưu tiên theo thứ tự:

1. **10 rule Never Violate** (architecture.md): cross-service = UUID không FK; Kafka chỉ qua Outbox ở matchmaking; idempotency guard ở booking/escrow; zombie check; courtPrice snapshot immutable; soft delete users/coaches; email-verified guard; KHÔNG VNPay.
2. **Đúng pattern**: Redis lock release trong `finally`; `@CircuitBreaker` fallback DB lock; Kafka manual ack + DLT (backoff 2s/4s/8s); `@PreAuthorize` trên **controller method** (không ở filter/service); `@Transactional` ở service layer.
3. **Correctness + edge case**: race condition, atomic counter âm/vượt totalSlots, state machine sai nhánh.
4. **Security**: secret hardcode, JWT, leak cross-service data, log password.
5. **Test coverage**: có `@Testcontainers` (KHÔNG H2)? endpoint có lock đã có race test chưa?

Feedback cụ thể, actionable. Thành thật về vấn đề thật, đừng khen xã giao.
