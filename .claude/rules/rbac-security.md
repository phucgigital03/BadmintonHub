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
- **Gateway filter:** Validate JWT on every request before routing. Rejected requests return 401 immediately.

```java
// Gateway JWT filter checks
// 1. Token present and not expired
// 2. jti not in Redis blacklist
// 3. Pass userId + roles in downstream headers: X-User-Id, X-User-Roles
```

## Google OAuth2

- `POST /api/auth/google` body: `{ idToken }` — verify via Google tokeninfo endpoint
- Upsert user: match on email → if new user, set `auth_provider=GOOGLE`, `password_hash=null`
- Issue same JWT + refresh token as local login
- `users.google_id` UNIQUE constraint

## Spring Security Config (per service)

Services behind the Gateway trust the forwarded `X-User-Id` and `X-User-Roles` headers. They do **not** re-validate the JWT — the Gateway is the single auth boundary.

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
        .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```
