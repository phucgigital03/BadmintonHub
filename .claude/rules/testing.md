# Testing — Developer Playbook

Phạm vi: việc viết test của **một developer** — test cho chính code mình build, chạy local trước commit.
(CI pipeline, JaCoCo coverage gate, e2e/QA cross-service riêng = **backlog**, không thuộc rule này.)

## Definition of Done của mỗi feature
Mỗi khi build/sửa logic ở `service/` hoặc endpoint ở `controller/`, kèm test trong **cùng thay đổi**:
1. **Unit test** cho business logic ở service layer.
2. **Integration test** cho endpoint/luồng (Testcontainers thật — KHÔNG H2).
3. `mvn -pl {service} verify` **xanh** trước khi commit.

Cách nhanh nhất: build xong → gõ **`/write-tests {service} {feature}`** (tự viết + chạy + fix).
Điểm dễ race (Redis lock, atomic counter) → **`/race-test {endpoint}`**.

## Test Pyramid

| Loại | File suffix | Plugin / lệnh | Dùng khi |
|---|---|---|---|
| **Unit** | `*Test.java` | surefire — `mvn test` | Logic service layer; `@ExtendWith(MockitoExtension.class)`, mock Feign/Kafka/Redis. Nhanh, viết nhiều nhất. |
| **Slice** | `*Test.java` | surefire | `@WebMvcTest` (controller: `@Valid`, `@PreAuthorize`) · `@DataJpaTest` + Testcontainers PG (repository query). |
| **Integration** | `*IT.java` | failsafe — `mvn verify` | `@SpringBootTest` + base `common-test`; luồng end-to-end với PG/Redis/Kafka thật. |
| **Race** | `*IT.java` | failsafe | Bắn request song song (booking slot, join match) — qua `/race-test`. |

Naming method: `methodName_scenario_expectedResult` (vd `createBooking_slotAlreadyReserved_throwsConflict`).

## Nền dùng chung — module `common-test`
Service thêm 1 dependency là đủ bộ (Testcontainers + base class + helper):
```xml
<dependency>
    <groupId>com.badmintonhub</groupId>
    <artifactId>common-test</artifactId>
    <scope>test</scope>
</dependency>
```

- **`AbstractIntegrationTest`** — `extends` để có sẵn PostgreSQL + Redis thật (Testcontainers singleton,
  start 1 lần, reuse mọi test class). `@SpringBootTest` + `@DynamicPropertySource` đã wire datasource/redis.
- **`AbstractKafkaIntegrationTest`** — `extends` khi service dùng Kafka (thêm Kafka container). Service KHÔNG
  dùng Kafka thì extends `AbstractIntegrationTest` để khỏi trả giá khởi động Kafka.
- **`JwtTestTokens`** — mint JWT test cho endpoint secured, khớp claim của `JwtUtil`:
  `JwtTestTokens.bearer(jwtSecret, userId, "ROLE_USER")` → đặt vào header `Authorization`.

```java
class BookingControllerIT extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Value("${jwt.secret}") String jwtSecret;

    @Test
    void createBooking_validSlot_returns201() throws Exception {
        mockMvc.perform(post("/api/bookings")
                .header("Authorization", JwtTestTokens.bearer(jwtSecret, userId, "ROLE_USER"))
                .contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }
}
```

## Quy ước bắt buộc
- **KHÔNG H2** cho test JPA — luôn Testcontainers PostgreSQL (qua `common-test`).
- **KHÔNG bỏ qua test đỏ** (`@Disabled`/comment) để build xanh — fix hoặc xoá có chủ đích.
- Unit test mock external dependency; integration test dùng infra thật.
- Async/Kafka: assert bằng **Awaitility** (`await().atMost(...)`), không `Thread.sleep`.
- Feign `lb://` call ra service khác: mock bằng **WireMock** hoặc `@MockBean` client.
- Assert bằng **AssertJ** (`assertThat(...)`).

## Lệnh
```bash
mvn -pl {service} test       # chỉ unit/slice (*Test) — nhanh
mvn -pl {service} verify     # unit + integration (*IT, Testcontainers) — chạy trước commit
mvn clean install -DskipTests   # build bỏ qua cả surefire + failsafe
```

## Backlog (chưa bật — ngoài phạm vi dev)
GitHub Actions CI · JaCoCo coverage gate · e2e/QA suite cross-service · load test.
