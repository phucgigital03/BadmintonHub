---
description: Lên kế hoạch trước khi viết code (đọc rule + convention BadmintonHub)
argument-hint: [service + mục tiêu, vd: booking-service reservation]
---
Trước khi viết BẤT KỲ code nào cho: **$ARGUMENTS**

1. Đọc rule liên quan trong .claude/rules/ — tối thiểu architecture.md, cộng theo loại việc: java-spring · database · kafka-patterns · redis-patterns · rbac-security · resilience · payment.
2. Đọc entity / service hiện có để theo đúng pattern đang dùng (đừng tự đặt convention mới).
3. Liệt kê file sẽ tạo/sửa + lý do, theo package chuẩn `com.badmintonhub.{service}.{entity|repository|service|controller|dto.request|dto.response}`.
4. Chỉ ra edge case + failure mode: race condition (Redis lock), Kafka retry → DLT, idempotency, zombie event, state machine đi nhánh sai.
5. Đối chiếu **10 rule Never Violate** — plan KHÔNG được vi phạm (không FK cross-service, Kafka chỉ qua Outbox ở matchmaking, KHÔNG VNPay/payment gateway, snapshot courtPrice immutable).
6. Đề xuất hướng làm + thứ tự (entity → repository → service → controller → test) rồi **DỪNG, chờ tôi duyệt**.

Chưa viết code.
