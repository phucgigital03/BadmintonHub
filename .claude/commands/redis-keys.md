---
allowed-tools: Bash(docker:*), Read
argument-hint: [pattern] (vd: "lock:*" hoặc "payment:countdown:*")
description: Inspect Redis keys — lock, countdown, blacklist, rate-limit
---
Inspect Redis (container `redis`) cho pattern **$ARGUMENTS**:

1. Liệt kê key: `docker exec redis redis-cli --scan --pattern "$ARGUMENTS"`
2. Với mỗi key: hiện TTL (`redis-cli TTL <key>`) + value (`redis-cli GET <key>`). TTL = -1 (không hạn) trên một `lock:*` là BUG — cảnh báo ngay.
3. Đối chiếu **Key Registry** trong .claude/rules/redis-patterns.md: TTL có khớp không, service nào sở hữu key.

Pattern hay debug: `lock:slot:*`, `lock:match:*`, `payment:countdown:*`, `session:blacklist:*`, `match:*:slots`, `rate_limit:*`. Lock kẹt (TTL cao bất thường) → nghi vấn `releaseRedisLock` không nằm trong `finally`.
