---
allowed-tools: Read, Grep, Glob
argument-hint: [file, class hoặc endpoint]
description: Giải thích code lạ trong context microservices BadmintonHub
---
Giải thích **$ARGUMENTS** trong context dự án BadmintonHub (tra .claude/rules/architecture.md để biết service nào làm gì, port + DB nào):

1. **Làm gì** — trách nhiệm của đoạn code, thuộc service/module nào.
2. **Ăn khớp ra sao** — gọi / được gọi bởi service nào: Feign `lb://`, Kafka topic (đối chiếu Topic Registry trong kafka-patterns.md), Redis key (redis-patterns.md). Nếu có cross-service ref (UUID) → trỏ tới bảng nào ở service nào.
3. **Ai phụ thuộc** — Kafka consumer/producer, scheduler, hay endpoint nào dựa vào nó.
4. **Rủi ro khi sửa** — có đụng 1 trong 10 rule "Never Violate" không (Outbox-only ở matchmaking, idempotency guard, zombie check, courtPrice snapshot immutable, soft delete, email-verified guard)? Chỉ rõ.

Chỉ đọc, KHÔNG sửa code.
