---
allowed-tools: Bash(mvn:*), Bash(docker:*), Read, Write, Edit, Grep, Glob
argument-hint: [class, service hoặc feature vừa build]
description: Viết & chạy test cho chức năng vừa build (Testcontainers qua common-test, no H2)
---
Đọc trước: .claude/rules/testing.md (playbook), .claude/rules/java-spring.md (mục Testing).

Viết + chạy test cho: **$ARGUMENTS**

1. **Xác định service** chứa chức năng và đọc code vừa build (service/controller/entity liên quan).
2. **Đảm bảo nền test**: nếu `{service}/pom.xml` chưa có `common-test` (scope test) → thêm:
   ```xml
   <dependency>
       <groupId>com.badmintonhub</groupId><artifactId>common-test</artifactId><scope>test</scope>
   </dependency>
   ```
3. **Unit test** (`{Class}Test.java`, surefire): `@ExtendWith(MockitoExtension.class)`, mock mọi external
   dependency (Feign client, KafkaTemplate, RedisTemplate). Phủ happy + edge + error của logic service.
4. **Integration test** (`{Class}IT.java`, failsafe): `@SpringBootTest` + **`extends AbstractIntegrationTest`**
   (hoặc `AbstractKafkaIntegrationTest` nếu chức năng dùng Kafka) — PostgreSQL/Redis THẬT qua `common-test`,
   **KHÔNG H2, KHÔNG tự new container**. Endpoint secured → header `Authorization` từ
   `JwtTestTokens.bearer(jwtSecret, userId, "ROLE_USER")`.
5. Điểm có Redis lock / atomic counter → thêm test concurrency (hoặc gọi thẳng `/race-test`).
6. Naming: `methodName_scenario_expectedResult`. Assert bằng AssertJ; async/Kafka chờ bằng Awaitility.
7. **Chạy `mvn -pl {service} verify`** (kích surefire `*Test` + failsafe `*IT`), fix tới khi xanh.
   Báo cáo test nào pass/fail — **KHÔNG bỏ qua test đỏ**.
