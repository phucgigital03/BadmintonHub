---
allowed-tools: Bash(mvn:*), Bash(docker:*), Read, Edit, Grep, Glob
argument-hint: [class, service hoặc feature]
description: Viết & chạy test theo chuẩn BadmintonHub (Testcontainers, no H2)
---
Đọc trước: .claude/rules/java-spring.md (mục Testing).

Viết test cho: **$ARGUMENTS**

1. **Unit test**: `@ExtendWith(MockitoExtension.class)`, mock mọi external dependency (Feign client, KafkaTemplate, RedisTemplate).
2. **Integration test**: `@SpringBootTest` + `@Testcontainers` với PostgreSQL / Redis THẬT — KHÔNG dùng H2, KHÔNG `@DataJpaTest` trên H2. Kafka cần thì dùng embedded Kafka / Testcontainers Kafka.
3. Phủ **happy path + edge case + error handling**. Endpoint có Redis lock / atomic counter → thêm test concurrency (hoặc gọi thẳng `/race-test`).
4. Naming: `methodName_scenario_expectedResult`.
5. Chạy `mvn -pl {service} test`, fix tới khi xanh. Báo cáo test nào pass/fail, KHÔNG bỏ qua test đỏ.
