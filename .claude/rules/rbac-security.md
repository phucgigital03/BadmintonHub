---
description: RBAC roles, permission matrix, Spring Security configuration, JWT setup, and endpoint authorization rules for BadmintonHub.
globs: **/*Controller*.java, **/*Security*.java, **/*Config*.java, **/*Filter*.java, **/*Auth*.java, **/*Jwt*.java
alwaysApply: false
---

# RBAC & Security

## Roles

| Role | Spring Authority | Auto-assigned |
|---|---|---|
| `ADMIN` | `ROLE_ADMIN` | Manual only |
| `STAFF` | `ROLE_STAFF` | Manual only |
| `COACH` | `ROLE_COACH` | Via coach approval flow |
| `USER` | `ROLE_USER` | Auto on register |

## Key Permission Rules

- `STAFF`/`ADMIN` **cannot** join a match — only manage
- Only `USER`/`COACH` can book courts, join matches, enroll with coaches, buy event tickets
- `ADMIN` only for: soft delete users, role assignment, coach approval, hard admin ops
- `STAFF` can: confirm/reject payments, force cancel bookings/matches, suspend coaches
- **Email verified guard:** `POST /api/bookings` and `POST /api/matches/{id}/join` require `is_email_verified=true`

## Permission Matrix (Key Endpoints)

| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `POST /api/auth/register` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/bookings` | ✅ (email verified) | ✅ | ✅ | ✅ |
| `POST /api/matches` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/matches/{id}/join` | ✅ (email verified) | ✅ | ❌ | ❌ |
| `POST /api/payments/{id}/confirm` | ❌ | ❌ | ✅ | ✅ |
| `POST /api/payments/{id}/refund` | ❌ | ❌ | ✅ | ✅ |
| `PATCH /api/coaches/{id}/approve` | ❌ | ❌ | ❌ | ✅ |
| `DELETE /api/users/{id}` | ❌ | ❌ | ❌ | ✅ (soft) |
| `GET /api/users` (list all) | ❌ | ❌ | ✅ | ✅ |

## `@PreAuthorize` Usage

Always put on the controller method, never in filters or service layer:

```java
// Single role
@PreAuthorize("hasRole('USER')")

// Multiple roles
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")

// Own resource OR privileged role
@PreAuthorize("#userId == authentication.principal.id or hasAnyRole('STAFF', 'ADMIN')")

// Email verified check (custom SpEL bean)
@PreAuthorize("hasRole('USER') and @authService.isEmailVerified(authentication)")

// STAFF/ADMIN can see all; user sees own
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN') or #userId == authentication.principal.id")
```

## JWT Configuration

- **Access token TTL:** 15 minutes — signed with RS256 or HS256
- **Refresh token TTL:** 30 days — stored as bcrypt hash in `users.refresh_token_hash`, sent as HttpOnly SameSite=Strict cookie
- **Blacklist:** On logout, add `jti` to Redis `session:blacklist:{jti}` with TTL = remaining token lifetime
- **Gateway filter:** Validate JWT on every request before routing. Rejected requests return 401 immediately with `{ code, message, timestamp }` (codes: `TOKEN_MISSING` / `TOKEN_EXPIRED` / `TOKEN_INVALID` / `TOKEN_REVOKED`).

```java
// Gateway JwtAuthenticationFilter checks (GlobalFilter, HIGHEST_PRECEDENCE)
// 1. Skip public paths: /api/auth/**, /actuator/**
// 2. Token present, signature valid, not expired
// 3. jti not in Redis session:blacklist:{jti}  (fail-open if Redis is down)
// 4. Forward the verified `Authorization: Bearer` token UNCHANGED downstream.
//    Do NOT emit X-User-Id / X-User-Roles — the token is the single source of identity.
//    (userId is stashed in a gateway-internal exchange attribute only, to key the rate limiter.)
```

JWT verification uses the shared `com.badmintonhub.security.JwtUtil` (module `common-security`,
web/JPA-free) — the same class both the gateway and every downstream service depend on.

## Google OAuth2

- `POST /api/auth/google` body: `{ idToken }` — verify via Google tokeninfo endpoint
- Upsert user: match on email → if new user, set `auth_provider=GOOGLE`, `password_hash=null`
- Issue same JWT + refresh token as local login
- `users.google_id` UNIQUE constraint

## Spring Security Config (per service)

**Defense in depth — every service re-validates the JWT.** The Gateway is the first auth gate, but
it is **not** the only one. Each service runs its own filter that re-validates the forwarded
`Authorization: Bearer` token with `com.badmintonhub.security.JwtUtil` and derives `userId` + roles
from the verified claims. The token is the single source of identity — services do **not** trust any
`X-User-Id` / `X-User-Roles` headers (the Gateway does not send them).

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated()
        )
        // re-validates Bearer token via shared JwtUtil, populates Authentication from claims
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

> The per-service `jwtAuthFilter` is built when each service is implemented (Day 4+). Today only the
> shared `JwtUtil` (module `common-security`) exists for them to reuse.
