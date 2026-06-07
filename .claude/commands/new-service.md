---
allowed-tools: Read, Write, Edit, Glob, Bash(mvn:*), Bash(curl:*)
argument-hint: [service-name] (vd: court-service)
description: Scaffold một microservice mới đúng convention BadmintonHub
---
Đọc trước: .claude/rules/architecture.md, .claude/rules/java-spring.md, .claude/rules/eureka-config.md, .claude/rules/database.md, .claude/rules/rbac-security.md.

Scaffold service **$ARGUMENTS** theo đúng convention dự án — CHỈ skeleton, KHÔNG business logic:

1. Tra port + `spring.application.name` từ bảng port trong architecture.md (khớp 100%).
2. `pom.xml`: parent = `badmintonhub`, thêm `spring-cloud-starter-netflix-eureka-client` + (web / data-jpa / postgresql nếu service có DB; data-mongodb nếu notification; không JPA cho ai-service).
3. Package `com.badmintonhub.{domain}` với các package con: `entity`, `repository`, `service`, `controller`, `dto.request`, `dto.response`, `exception`, `config`.
4. Main class `@SpringBootApplication`. Service KHÔNG dùng JPA (notification/ai) → exclude `DataSourceAutoConfiguration` + `HibernateJpaAutoConfiguration`.
5. `application.yml`: `server.port` + `spring.application.name` + eureka client (eureka-config.md) + datasource dạng `${VAR:default}`.
6. `GatewayHeaderAuthFilter` + `SecurityFilterChain` (rbac-security.md) — service TIN header `X-User-Id`/`X-User-Roles`, KHÔNG re-validate JWT.
7. Chạy `mvn -pl $ARGUMENTS spring-boot:run` tới khi service register Eureka UP (`curl -s http://localhost:8761/eureka/apps`).

DỪNG trước khi viết entity domain — đó là việc của prompt từng Day, không phải của scaffold.
