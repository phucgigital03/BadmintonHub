
Sơ đồ A — 3 ô → key=slotId → mỗi luồng vào 1 partition, giữ thứ tự
```mermaid
flowchart LR
    subgraph BK["booking-service · OutboxWriter — key = slotId"]
        direction TB
        H1["HELD ô18:00 · eid a1"]
        R1["RELEASED ô18:00 · eid a2"]
        H2["HELD ô18:30 · eid b1"]
        R2["RELEASED ô18:30 · eid b2"]
        H3["HELD ô19:00 · eid c1"]
        R3["RELEASED ô19:00 · eid c2"]
    end

    subgraph KFK["topic booking.slot.changed"]
        direction TB
        P0["Partition cho key 18:00<br/>off0 HELD → off1 RELEASED"]
        P1["Partition cho key 18:30<br/>off0 HELD → off1 RELEASED"]
        P2["Partition cho key 19:00<br/>off0 HELD → off1 RELEASED"]
    end

    C0["court ô18:00<br/>RESERVED → AVAILABLE ✔"]
    C1["court ô18:30<br/>RESERVED → AVAILABLE ✔"]
    C2["court ô19:00<br/>RESERVED → AVAILABLE ✔"]

    H1 & R1 -->|"key 18:00"| P0
    H2 & R2 -->|"key 18:30"| P1
    H3 & R3 -->|"key 19:00"| P2
    P0 --> C0
    P1 --> C1
    P2 --> C2
```

Sơ đồ B — Một ô (18:00): giữ thứ tự + chống gửi lặp khi replay
```mermaid
sequenceDiagram
    participant B as booking-service
    participant K as Kafka · partition key=18:00
    participant C as court-service
    participant DB as processed_events

    B->>K: off0 HELD ô18:00 (eid a1)
    B->>K: off1 RELEASED ô18:00 (eid a2)
    Note over K: cùng key → cùng partition → off0 trước off1

    K->>C: giao off0 HELD (a1)
    C->>DB: a1 chưa có → lật RESERVED + lưu a1
    Note over C: crash NGAY TRƯỚC khi ack
    K->>C: giao lại off0 HELD (a1)
    C->>DB: a1 ĐÃ có → bỏ qua (không lật 2 lần)
    C-->>K: ack off0

    K->>C: giao off1 RELEASED (a2)
    C->>DB: a2 chưa có → lật AVAILABLE + lưu a2
    C-->>K: ack off1
    Note over C: ô 18:00 kết thúc AVAILABLE ✔
```


Sơ đồ thật — giả sử topic chỉ có 2 partition, 18:00 và 19:00 lỡ đụng chung
```mermaid
flowchart LR
    H1["HELD 18:00 · a1"]
    R1["RELEASED 18:00 · a2"]
    H2["HELD 18:30 · b1"]
    R2["RELEASED 18:30 · b2"]
    H3["HELD 19:00 · c1"]
    R3["RELEASED 19:00 · c2"]

    H1 & R1 -->|"hash(18:00) mod 2 = 0"| P0
    H3 & R3 -->|"hash(19:00) mod 2 = 0"| P0
    H2 & R2 -->|"hash(18:30) mod 2 = 1"| P1

    P0["Partition 0 (chứa 2 key 18:00 + 19:00)<br/>off0 HELD18:00 · off1 HELD19:00 · off2 RELEASED18:00 · off3 RELEASED19:00"]
    P1["Partition 1 (key 18:30)<br/>off0 HELD18:30 · off1 RELEASED18:30"]
```


Sơ đồ 1 — SAI: không key theo slotId → cùng ô bị tách partition
```mermaid
flowchart LR
    H1["HELD ô18:00 · eid a1"]
    R1["RELEASED ô18:00 · eid a2"]

    H1 -->|"key = a1 ngẫu nhiên → hash ra X"| PX["Partition X"]
    R1 -->|"key = a2 ngẫu nhiên → hash ra Y"| PY["Partition Y"]

    PX --> C["court-service<br/>2 partition đọc SONG SONG<br/>không có thứ tự chéo partition"]
    PY --> C
```