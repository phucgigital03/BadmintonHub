---
description: Java and Spring Boot coding conventions for BadmintonHub backend services — package structure, entities, DTOs, controllers, API design, and cross-service communication patterns.
globs: **/*.java
alwaysApply: false
---

# Java / Spring Boot Conventions

## Package Structure

```
com.badmintonhub.{service-name}.{domain}

Examples:
  com.badmintonhub.booking.entity.Booking
  com.badmintonhub.booking.service.BookingService          (interface — the contract)
  com.badmintonhub.booking.service.impl.BookingServiceImpl (@Service implementation)
  com.badmintonhub.booking.controller.BookingController
  com.badmintonhub.booking.dto.request.CreateBookingRequest
  com.badmintonhub.booking.dto.response.BookingResponse
  com.badmintonhub.booking.repository.BookingRepository
  com.badmintonhub.booking.exception.BookingNotFoundException
```

## Entities

- Extend `BaseAuditEntity` from `common` module for `created_at` / `updated_at`
- Add `@EntityListeners(AuditingEntityListener.class)` on entity class
- All PKs are `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`
- Cross-service references: plain `UUID` field, **no `@ManyToOne`**, column comment `"ref table.id · cross-service UUID"`
- Soft delete: `@Column(name = "deleted_at") private LocalDateTime deletedAt;` + `@Where(clause = "deleted_at IS NULL")`
- Enums stored as `@Enumerated(EnumType.STRING)`

```java
@Entity
@Table(name = "bookings")
@EntityListeners(AuditingEntityListener.class)
@Where(clause = "deleted_at IS NULL")
public class Booking extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "uuid COMMENT 'ref users.id · cross-service UUID'")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;
}
```

## DTOs

- Separate request and response classes — never expose entities directly
- Request DTOs: use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Max`, `@Pattern`, `@Future`)
- Response DTOs: use `record` or plain POJO with Lombok `@Value` / `@Data`
- Map entity ↔ DTO in service layer, not controller

```java
public record CreateBookingRequest(
    @NotNull UUID slotId,
    @NotNull UUID courtId
) {}

public record BookingResponse(UUID id, BookingStatus status, LocalDateTime createdAt) {}
```

## Controllers

- `@PreAuthorize` on every secured endpoint — not in filters or interceptors
- `@Valid` on all request body parameters
- Return `ResponseEntity<T>` with explicit status codes
- Use `@RestControllerAdvice` for global exception handling → `{ code, message, timestamp }`

```java
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    @PostMapping
    @PreAuthorize("hasRole('USER') and @authService.isEmailVerified(authentication)")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(req));
    }
}
```

## Service Layer

- **Interface + impl split**: declare the contract as an **interface** in `service/`, put the implementation in `service/impl/` as `{Name}ServiceImpl` annotated `@Service`. Controllers and other services inject the **interface** (by type), never the Impl.
- `@Transactional` and all business logic live in the **Impl** — never in the interface, controllers, or repositories.
- For distributed operations: acquire Redis lock → business logic → release lock (in `finally`).
- **SpEL bean-name caveat**: if a method is referenced by name in `@PreAuthorize` (e.g. `@authService.isEmailVerified(authentication)` for rule #10), annotate the impl with the explicit bean name — `@Service("authService")` — so the documented name resolves regardless of the `*Impl` class name.

```java
public interface BookingService {
    BookingResponse create(CreateBookingRequest req);
}

// service/impl/BookingServiceImpl.java
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    @Override
    @Transactional
    public BookingResponse create(CreateBookingRequest req) { ... }
}
```

## API Conventions

- Base path: `/api/{resource}` (plural, kebab-case) e.g. `/api/bookings`, `/api/time-slots`
- Paginated lists: `?page=0&size=20&sort=createdAt,desc` → return `Page<T>`
- Error response shape: `{ "code": "SLOT_NOT_AVAILABLE", "message": "...", "timestamp": "..." }`

## Cross-Service Calls (Within Same Request)

Use Feign client or `WebClient` with `lb://` URI — never `http://localhost:{port}`:

```java
@FeignClient(name = "court-service")   // matches spring.application.name
public interface CourtServiceClient {
    @GetMapping("/api/courts/{id}/slots/{slotId}")
    SlotResponse getSlot(@PathVariable UUID id, @PathVariable UUID slotId);
}
```

## Kafka Consumers

Always use manual acknowledgment — never auto-commit:

```java
@KafkaListener(topics = "payment.player.confirmed", groupId = "booking-service",
               containerFactory = "manualAckListenerContainerFactory")
public void onPlayerPaymentConfirmed(ConsumerRecord<String, String> record,
                                     Acknowledgment ack) {
    // idempotency check first
    if (processedEventRepo.existsById(record.key())) {
        ack.acknowledge();
        return;
    }
    // ... business logic
    processedEventRepo.save(new ProcessedEvent(record.key()));
    ack.acknowledge();
}
```

## Testing

Full playbook: **`.claude/rules/testing.md`** (test pyramid, `common-test` base, `*Test`/`*IT`, `mvn verify`).

- Unit tests (`*Test.java`): `@ExtendWith(MockitoExtension.class)` — mock all external dependencies
- Integration tests (`*IT.java`): `@SpringBootTest` + `extends AbstractIntegrationTest` (module `common-test` — Testcontainers PostgreSQL/Redis, **no H2**)
- Secured endpoints in tests: `JwtTestTokens.bearer(secret, userId, "ROLE_USER")`
- Test naming: `methodName_scenario_expectedResult`
