---
allowed-tools: Bash(curl:*), Bash(docker:*), Bash(git:*), Bash(mvn:*), Bash(npm:*), Read, Edit, Write, Grep, Glob
argument-hint: [mô tả bug / lỗi / triệu chứng]
description: Fix bug có kỷ luật — systematic debugging (reproduce → root cause → fix → verify)
---
Fix bug bằng **systematic debugging** — không đoán mò, không vá triệu chứng: **$ARGUMENTS**

## Nghi can thay đổi gần đây (regression?)
Commit gần nhất:
!`git log --oneline -6`
Thay đổi chưa commit:
!`git status --short`

## Quy trình — đi tuần tự, KHÔNG nhảy bước
1. **Tái hiện + chốt triệu chứng.** Lấy lỗi CHÍNH XÁC (message / stack / HTTP code / console), đừng diễn giải lại. Chưa tái hiện được thì tái hiện trước — KHÔNG fix cái không reproduce. Cố rút về 1 lệnh: `curl -i ...` (FE → thêm `-X OPTIONS` test preflight), 1 thao tác UI, hoặc 1 test fail.
2. **Đọc bằng chứng THẬT — đừng đoán.** Log service (terminal `mvn spring-boot:run`), Network/Console của browser, stack trace tới **dòng đầu tiên thuộc code mình**. Thu hẹp: lỗi ở layer nào — FE / gateway / service / DB / Kafka / Redis? Hỏi "cái gì vừa đổi?" (mục Nghi can ở trên).
3. **Một giả thuyết root-cause (chứng minh được).** Phát biểu nguyên nhân gốc + cách kiểm chứng. Phân biệt **triệu chứng** (FE thấy 500) vs **gốc** (handler chưa scan / bean cycle / thiếu env). Đối chiếu bảng dưới.
4. **Kiểm chứng — đổi MỘT biến.** Xác nhận giả thuyết bằng grep / đọc code / log / `docker exec ... psql`, chưa sửa gì cả. Sai giả thuyết → quay lại bước 2, KHÔNG chồng fix lên nhau.
5. **Fix gốc, tối thiểu, đúng layer.** Đối chiếu **10 Never-Violate** (architecture.md). Fix lớn / đụng nhiều file → nêu trước rồi HỎI, đừng tự refactor rộng.
6. **Verify bằng cách tái hiện LẠI.** Chạy đúng bước (1) → lỗi BIẾN MẤT. Build lại (`mvn -pl {service} -am compile` hoặc `npm run build`). Soi có vỡ chỗ khác không (regression). **Chưa verify thì KHÔNG nói "đã fix".**
7. **Phòng tái phát.** Thêm test (`/write-tests`) hoặc guard; ghi 1 dòng bài học.

## Failure-mode map (BadmintonHub)
| Triệu chứng | Nghi gốc | Soi ở |
|---|---|---|
| CORS / preflight bị block | gateway thiếu `globalcors`, hoặc origin `*` + `withCredentials` | api-gateway/application.yml |
| APPLICATION FAILED TO START | bean cycle (`SecurityConfig ↔ JwtAuthFilter`) · thiếu env (`JWT_SECRET` fail-fast) · port bận | log context, SecurityConfig |
| 401/403 sai | JWT filter · `@PreAuthorize` SpEL (principal = userId String → `authentication.name`, KHÔNG `principal.id`) · jti blacklist | JwtAuthFilter, controller |
| 404 / "no instances available" | service chưa register Eureka · route gateway sai · chạy `mvn` KHÔNG từ root (`.env` không nạp) | http://localhost:8761, eureka-config.md |
| 500 thay vì `{code,message,timestamp}` | GlobalExceptionHandler chưa áp (auto-config) | common/config/CommonWebAutoConfiguration |
| Kafka kẹt / rớt vào `.DLT` | consumer lag · idempotency · zombie event | gọi `/kafka-trace {topic}` |
| Double-book / counter âm-vượt | Redis lock / atomic counter sai | `/redis-keys`, `/race-test` |
| `.env` không nạp khi run | `mvn` chạy sai CWD | chạy `mvn` từ thư mục **ROOT** dự án |

## Luật cứng (senior discipline)
- KHÔNG shotgun: sửa **1 thứ / lần**, có bằng chứng mới sửa.
- KHÔNG vá triệu chứng khi biết gốc nằm chỗ khác (fix ĐÚNG layer).
- KHÔNG tuyên bố "fixed" nếu chưa tái hiện lại và thấy lỗi hết.
- Báo cáo trung thực: **gốc là gì · đã sửa gì · verify thế nào** (kèm output).
