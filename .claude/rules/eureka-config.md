---
description: Eureka service discovery configuration — eureka-server setup, client registration for every service, and API Gateway lb:// routing. Apply when editing application.yml, pom.xml, or Gateway config.
globs: **/application.yml, **/application.yaml, **/pom.xml, **/*GatewayConfig*.java, **/*RouteConfig*.java
alwaysApply: false
---

# Eureka Service Discovery

## eureka-server module

`pom.xml` dependencies:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Main class:
```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication { ... }
```

`application.yml`:
```yaml
spring:
  application:
    name: eureka-server
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    wait-time-in-ms-when-sync-empty: 0
    enable-self-preservation: false   # disable in dev; enable in prod
```

Dashboard: http://localhost:8761

## Every Other Service (Client Config)

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

No `@EnableDiscoveryClient` annotation needed — Spring Boot auto-detects the dependency.

Add to `application.yml`:
```yaml
spring:
  application:
    name: booking-service    # MUST be unique — this is the service name used in lb://

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true                  # use IP not hostname (critical in Docker)
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

## API Gateway Routing

**Never** use `http://localhost:{port}` in Gateway routes. Always use `lb://` with Eureka service name.

`api-gateway/application.yml`:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service          # spring.application.name = "user-service"
          predicates:
            - Path=/api/auth/**, /api/users/**
        - id: court-service
          uri: lb://court-service
          predicates:
            - Path=/api/courts/**, /api/clubs/**
        - id: booking-service
          uri: lb://booking-service
          predicates:
            - Path=/api/bookings/**
        - id: matchmaking-service
          uri: lb://matchmaking-service
          predicates:
            - Path=/api/matches/**
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**, /api/bank-accounts/**, /api/bank-transactions/**
        - id: escrow-service
          uri: lb://escrow-service
          predicates:
            - Path=/api/escrow/**
        - id: coach-service
          uri: lb://coach-service
          predicates:
            - Path=/api/coaches/**
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**
        - id: event-service
          uri: lb://event-service
          predicates:
            - Path=/api/events/**
        - id: ai-service
          uri: lb://ai-service
          predicates:
            - Path=/api/ai/**
```

## Docker Compose

```yaml
eureka-server:
  build: ./eureka-server
  ports:
    - "8761:8761"
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
    interval: 10s
    timeout: 5s
    retries: 5

user-service:
  build: ./user-service
  ports:
    - "3001:3001"
  depends_on:
    eureka-server:
      condition: service_healthy
  environment:
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
    EUREKA_INSTANCE_PREFER_IP_ADDRESS: "true"
```

All services (except `eureka-server` itself) need `depends_on: eureka-server` and the `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` env var.

## Service Name → Port Reference

| spring.application.name | Port |
|---|---|
| `eureka-server` | 8761 |
| `api-gateway` | 3000 |
| `user-service` | 3001 |
| `court-service` | 3002 |
| `booking-service` | 3003 |
| `matchmaking-service` | 3004 |
| `coach-service` | 3005 |
| `payment-service` | 3006 |
| `escrow-service` | 3007 |
| `notification-service` | 3008 |
| `event-service` | 3009 |
| `ai-service` | 3010 |
