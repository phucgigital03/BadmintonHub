---
allowed-tools: Bash(mvn:*), Bash(curl:*), Bash(docker:*), Read, Grep
argument-hint: [service-name] (vd: booking-service)
description: Chạy Definition of Done — build + Eureka UP + smoke test
---
Eureka registry hiện tại: !`curl -s http://localhost:8761/eureka/apps | grep -oE "<name>[^<]+</name>|<status>[^<]+</status>" | paste - - 2>/dev/null || echo "Eureka chưa chạy"`

Verify service **$ARGUMENTS** đã "Done":

1. Build từ root: chạy `mvn -q -pl $ARGUMENTS -am clean install -DskipTests` → phải BUILD SUCCESS.
2. Eureka: kiểm tra **$ARGUMENTS** xuất hiện với status UP trong registry ở trên.
3. Đọc mục **"Định nghĩa Done"** của ngày tương ứng với service này trong IMPLEMENTATION_GUIDE.md, chạy ĐÚNG các lệnh `curl` ở đó.
4. Báo cáo ✅/❌ từng tiêu chí. Nếu ❌ → chỉ rõ nguyên nhân (log service, Eureka chưa UP, Kafka lag, Redis key) rồi HỎI trước khi sửa rộng — không tự ý refactor.
