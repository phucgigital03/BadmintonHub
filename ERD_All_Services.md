# 🏸 BadmintonHub — Full System ERD (All Services)

> **Architecture**: Database-per-Service · 9 microservices · PostgreSQL × 8 · MongoDB × 1  
> **Cross-service references**: UUID only — no FK constraints across databases.  
> Consistency enforced via **Kafka events + Saga Pattern + Outbox Pattern**.  
> Dashed relationships (`}o..o{`) = logical cross-service links (UUID ref, no DB-level FK).

---

```mermaid
erDiagram

    %% ═══════════════════════════════════════════════════════
    %% user_db  (user-service · PostgreSQL · port 3001)
    %% Owns: auth, identity, roles, audit trail
    %% ═══════════════════════════════════════════════════════
    users {
        UUID        id                          PK
        string      email                       UK      "NOT NULL · unique per platform"
        string      password_hash                       "nullable — NULL for OAuth users"
        string      full_name                           "NOT NULL"
        string      phone                               "VN format: 0xxxxxxxxx"
        string      skill_level                         "BEGINNER|INTERMEDIATE|ADVANCED|PRO"
        string      avatar_url                          "Cloudinary URL"
        boolean     is_active                           "DEFAULT TRUE"
        boolean     is_email_verified                   "DEFAULT FALSE · gate for booking/joining"
        string      email_verify_token                  "nullable · hashed UUID stored in Redis 24h"
        timestamp   email_verify_expiry                 "nullable"
        string      refresh_token_hash                  "nullable · bcrypt hash of 30-day token"
        timestamp   refresh_token_expiry                "nullable"
        string      google_id                   UK      "nullable · OAuth2 Google sub"
        string      auth_provider                       "LOCAL|GOOGLE · DEFAULT LOCAL"
        string      fcm_token                           "nullable · Firebase Cloud Messaging device token"
        boolean     notification_email_enabled          "DEFAULT TRUE"
        boolean     notification_push_enabled           "DEFAULT TRUE"
        timestamp   deleted_at                          "nullable · soft delete"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    roles {
        UUID        id                          PK
        string      name                        UK      "ADMIN|STAFF|COACH|USER"
    }
    user_roles {
        UUID        user_id                     FK      "→ users.id"
        UUID        role_id                     FK      "→ roles.id"
        timestamp   assigned_at                         "NOT NULL DEFAULT NOW()"
        UUID        assigned_by                         "ref users.id · who granted this role"
    }
    audit_logs {
        UUID        id                          PK
        UUID        actor_id                            "ref users.id · who performed action"
        string      action                              "CANCEL_BOOKING|REFUND_PAYMENT|APPROVE_COACH|ASSIGN_ROLE..."
        string      resource                            "BOOKING|MATCH|PAYMENT|COACH|USER"
        UUID        resource_id                         "which record was affected"
        text        old_value                           "JSON snapshot before change (nullable)"
        text        new_value                           "JSON snapshot after change (nullable)"
        string      ip_address                          "IPv4 or IPv6 · nullable"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
    }

    %% ═══════════════════════════════════════════════════════
    %% court_db  (court-service · PostgreSQL · port 3002)
    %% Owns: clubs (venues), courts (physical sân), pricing rules, time slots, slot auto-gen, club reviews, geo
    %% Model: 1 club (CLB) ──< N courts (Sân 1..N) ──< N time_slots
    %% ═══════════════════════════════════════════════════════
    clubs {
        UUID        id                          PK
        UUID        created_by                          "ref users.id · STAFF or ADMIN"
        string      name                                "NOT NULL · e.g. An Bình Pickleball (tên CLB/venue)"
        string      address                             "NOT NULL"
        string      district                            "NOT NULL · for search filtering"
        json        images                              "Cloudinary URL array"
        boolean     is_active                           "DEFAULT TRUE · soft deactivation"
        decimal     latitude                            "DECIMAL(9,6) · for geo search"
        decimal     longitude                           "DECIMAL(9,6) · for geo search"
        decimal     rating                              "DECIMAL(3,2) DEFAULT 0.00 · avg of club_reviews"
        int         total_reviews                       "DEFAULT 0 · denormalized counter"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    courts {
        UUID        id                          PK
        UUID        club_id                     FK      "→ clubs.id"
        string      court_number                        "NOT NULL · e.g. Sân 1 | Sân 2 (1 physical court)"
        string      sport                               "PICKLEBALL|BADMINTON"
        string      type                                "SYNTHETIC|WOOD|CONCRETE"
        boolean     is_active                           "DEFAULT TRUE · soft deactivation"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    court_pricing_rules {
        UUID        id                          PK
        UUID        club_id                     FK      "→ clubs.id · price áp chung mọi sân cùng môn"
        string      sport                               "PICKLEBALL|BADMINTON"
        string      day_type                            "WEEKDAY (T2-T6) | WEEKEND (T7-CN)"
        time        start_time                          "NOT NULL · đầu khung giờ e.g. 05:00"
        time        end_time                            "NOT NULL · cuối khung giờ e.g. 10:00"
        string      customer_type                       "FIXED (Cố định) | WALK_IN (Vãng lai)"
        decimal     price_per_hour                      "NOT NULL · VND · authoritative price · UNIQUE(club_id,sport,day_type,start_time,customer_type)"
    }
    time_slots {
        UUID        id                          PK
        UUID        court_id                    FK      "→ courts.id (1 sân)"
        UUID        blocked_by                          "ref users.id · nullable · STAFF/ADMIN who blocked"
        date        date                                "NOT NULL"
        time        start_time                          "NOT NULL · granularity = ô bookable (vd 30 phút)"
        time        end_time                            "NOT NULL"
        string      status                              "AVAILABLE|RESERVED|BLOCKED|EVENT"
        UUID        event_id                            "ref events.id · nullable · set when status=EVENT"
        UUID        match_id                            "ref matches.id · nullable · set when RESERVED via match"
        UUID        booking_id                          "ref bookings.id (HEADER) · nullable · N slots của 1 đơn chung 1 booking_id"
        UUID        enrollment_id                       "ref coach_enrollments.id · nullable · set when RESERVED via coaching"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    club_reviews {
        UUID        id                          PK
        UUID        club_id                             "ref clubs.id · cross-service UUID"
        UUID        user_id                             "ref users.id · cross-service UUID"
        UUID        booking_id                  UK      "ref bookings.id · 1 booking = 1 review · UNIQUE constraint"
        smallint    rating                              "CHECK (rating BETWEEN 1 AND 5)"
        text        comment                             "nullable"
        boolean     is_flagged                          "DEFAULT FALSE · STAFF/ADMIN can flag abuse"
        UUID        flagged_by                          "ref users.id · nullable"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
    }

    %% ═══════════════════════════════════════════════════════
    %% booking_db  (booking-service · PostgreSQL · port 3003)
    %% Owns: court bookings (header + items · multi-slot order), idempotency guard, cancellation policy
    %% Model: 1 bookings (HEADER · 1 đơn = 1 thanh toán) ──< N booking_items (1 ô 30' nguyên tử)
    %% ═══════════════════════════════════════════════════════
    bookings {
        UUID        id                          PK
        UUID        user_id                             "ref users.id · cross-service UUID"
        UUID        club_id                             "ref clubs.id · cross-service UUID · 1 đơn = 1 CLB"
        string      customer_name                       "NOT NULL · từ form đặt"
        string      customer_phone                      "NOT NULL · từ form đặt"
        string      note                                "nullable · ghi chú cho chủ sân"
        string      customer_type                       "FIXED|WALK_IN · DEFAULT WALK_IN (app = vãng lai)"
        date        booking_date                        "NOT NULL · ngày chơi"
        decimal     total_price                         "NOT NULL · = SUM(booking_items.price) · snapshot"
        decimal     refund_amount                       "nullable · actual amount refunded per cancellation policy"
        string      status                              "PENDING|CONFIRMED|CANCELLED|COMPLETED"
        string      cancel_reason                       "nullable"
        UUID        cancelled_by                        "ref users.id · nullable · who cancelled"
        timestamp   earliest_start_time                 "NOT NULL snapshot · giờ bắt đầu sớm nhất trong đơn · dùng tính chính sách hoàn huỷ"
        timestamp   cancelled_at                        "nullable"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    booking_items {
        UUID        id                          PK
        UUID        booking_id                  FK      "→ bookings.id"
        UUID        court_id                            "ref courts.id (1 sân) · cross-service UUID"
        UUID        slot_id                     UK      "ref time_slots.id · cross-service UUID · 1:1 với 1 ô 30' · UNIQUE (1 slot ↔ 1 item)"
        string      court_name                          "snapshot · e.g. Sân 2"
        time        start_time                          "snapshot · e.g. 10:00"
        time        end_time                            "snapshot · e.g. 10:30"
        decimal     price                               "NOT NULL · snapshot giá ô 30' (từ court_pricing_rules tại thời điểm đặt)"
    }
    booking_processed_events {
        string      event_id                    PK      "Kafka message key or UUID · idempotency guard"
        timestamp   processed_at                        "NOT NULL DEFAULT NOW()"
    }

    %% ═══════════════════════════════════════════════════════
    %% matchmaking_db  (matchmaking-service · PostgreSQL · port 3004)
    %% Owns: matches, participants, outbox, waiting list
    %% ═══════════════════════════════════════════════════════
    matches {
        UUID        id                          PK
        UUID        club_id                             "ref clubs.id · cross-service UUID"
        UUID        court_id                            "ref courts.id (1 sân) · cross-service UUID"
        UUID        host_id                             "ref users.id · cross-service UUID"
        UUID        closed_by                           "ref users.id · nullable · STAFF/ADMIN who force-closed"
        date        date                                "NOT NULL"
        time        start_time                          "NOT NULL"
        time        end_time                            "NOT NULL"
        int         total_slots                         "NOT NULL · EVEN number: 2|4|6|8|...|16"
        int         filled_slots                        "NOT NULL DEFAULT 0"
        decimal     price_per_person                    "NOT NULL DEFAULT 0 · 0 = free match"
        decimal     court_price                         "NOT NULL · SNAPSHOT from court-service at creation time"
        string      sport                               "BADMINTON|PICKLEBALL"
        string      format                              "SINGLES|DOUBLES|MIXED"
        string      skill_required                      "BEGINNER|INTERMEDIATE|ADVANCED|PRO"
        string      status                              "PENDING_PAYMENT|OPEN|FULL|CANCELLED|COMPLETED"
        string      cancel_reason                       "nullable"
        timestamp   cancelled_at                        "nullable"
        timestamp   completed_at                        "nullable"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    match_slots {
        UUID        match_id                    FK      "→ matches.id"
        UUID        slot_id                     UK      "ref time_slots.id · cross-service UUID · 1 ô 30' match giữ · UNIQUE (1 slot ↔ 1 match)"
    }
    match_participants {
        UUID        match_id                    FK      "→ matches.id"
        UUID        user_id                             "ref users.id · cross-service UUID"
        UUID        payment_id                          "ref payments.id · nullable · null for free matches"
        timestamp   joined_at                           "NOT NULL DEFAULT NOW()"
        timestamp   left_at                             "nullable · set if player cancels participation"
    }
    match_waitlist {
        UUID        match_id                    FK      "→ matches.id"
        UUID        user_id                             "ref users.id · cross-service UUID"
        int         position                            "wait queue position (1-indexed)"
        timestamp   joined_at                           "NOT NULL DEFAULT NOW()"
    }
    outbox_events {
        UUID        id                          PK
        string      topic                               "NOT NULL · e.g. match.slot.joined"
        text        payload                             "NOT NULL · JSON string"
        string      status                              "PENDING|SENT"
        int         retry_count                         "DEFAULT 0 · for monitoring"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   sent_at                             "nullable · set when SENT"
    }

    %% ═══════════════════════════════════════════════════════
    %% escrow_db  (escrow-service · PostgreSQL · port 3007)
    %% Owns: court money escrow, player reimbursements, settlements
    %% Flow: HOST_DEPOSIT → PLAYER_REIMBURSEMENT(s) → COURT_OWNER_SETTLEMENT
    %%       or HOST_DEPOSIT → PLAYER_REIMBURSEMENT(s) → REFUNDS (on cancel)
    %% ═══════════════════════════════════════════════════════
    escrow_accounts {
        UUID        id                          PK
        UUID        match_id                    UK      "ref matches.id · 1 match = 1 escrow account"
        UUID        court_owner_id                      "ref users.id · who receives settlement on COMPLETED"
        decimal     amount                              "NOT NULL · court_price held in escrow"
        decimal     released_amount                     "NOT NULL DEFAULT 0 · cumulative reimbursed to Host"
        string      status                              "HOLDING|PARTIALLY_RELEASED|SETTLED|REFUNDED"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   settled_at                          "nullable · set when SETTLED or REFUNDED"
    }
    escrow_transactions {
        UUID        id                          PK
        UUID        escrow_id                   FK      "→ escrow_accounts.id"
        string      type                                "HOST_DEPOSIT|PLAYER_REIMBURSEMENT|COURT_OWNER_SETTLEMENT|HOST_REFUND|PLAYER_REFUND"
        UUID        from_party_id                       "ref users.id or system"
        UUID        to_party_id                         "ref users.id or system"
        decimal     amount                              "NOT NULL · VND"
        UUID        reference_payment_id                "ref payments.id · nullable · the bank payment that triggered this"
        string      status                              "PENDING|COMPLETED|FAILED"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   completed_at                        "nullable"
    }
    escrow_processed_events {
        string      event_id                    PK      "idempotency guard for escrow Kafka consumers"
        timestamp   processed_at                        "NOT NULL DEFAULT NOW()"
    }

    %% ═══════════════════════════════════════════════════════
    %% payment_db  (payment-service · PostgreSQL · port 3006)
    %% Owns: bank accounts, payment orders, proof screenshots, manual refunds
    %% Flow: initiate → show QR → user transfers + uploads proof → STAFF confirms → CONFIRMED
    %% ═══════════════════════════════════════════════════════
    bank_accounts {
        UUID        id                          PK
        string      bank_name                           "NOT NULL e.g. Shinhan Bank VN / MB Bank"
        string      account_number              UK      "NOT NULL"
        string      account_name                        "NOT NULL e.g. Trần Quốc Phú"
        string      qr_image_url                        "Cloudinary URL of the bank QR image"
        boolean     is_active                           "DEFAULT TRUE · only active accounts shown on payment screen"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
    }
    payments {
        UUID        id                          PK
        string      order_code                  UK      "SERIAL-based e.g. #184 · displayed on screen · user writes this in transfer note"
        UUID        booking_id                          "ref bookings.id · nullable · cross-service UUID"
        UUID        match_id                            "ref matches.id · nullable · cross-service UUID"
        UUID        enrollment_id                       "ref coach_enrollments.id · nullable · cross-service UUID"
        UUID        user_id                             "ref users.id · payer · cross-service UUID"
        UUID        bank_account_id             FK      "→ bank_accounts.id · which bank account to display"
        decimal     amount                              "NOT NULL · VND to transfer"
        decimal     refund_amount                       "nullable · VND refunded"
        string      payment_type                        "BOOKING|MATCH_HOST|MATCH_PLAYER|COACH_ENROLLMENT|EVENT_TICKET"
        string      status                              "PENDING|PROOF_SUBMITTED|CONFIRMED|EXPIRED|REFUNDED"
        timestamp   expires_at                          "NOT NULL · countdown deadline e.g. NOW()+10min · slot released if passed"
        UUID        confirmed_by                        "ref users.id · nullable · STAFF/ADMIN who clicked Confirm"
        timestamp   confirmed_at                        "nullable"
        string      reject_reason                       "nullable · set when STAFF rejects proof"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    payment_proofs {
        UUID        id                          PK
        UUID        payment_id                  FK      "→ payments.id"
        string      image_url                           "NOT NULL · Cloudinary URL of the transfer screenshot"
        UUID        uploaded_by                         "ref users.id · who uploaded"
        timestamp   uploaded_at                         "NOT NULL DEFAULT NOW()"
        UUID        reviewed_by                         "ref users.id · nullable · STAFF/ADMIN who reviewed"
        timestamp   reviewed_at                         "nullable"
        string      review_note                         "nullable · STAFF comment e.g. amount mismatch"
    }
    manual_refunds {
        UUID        id                          PK
        UUID        payment_id                  FK      "→ payments.id"
        decimal     amount                              "NOT NULL · VND to refund"
        string      refund_method                       "BANK_TRANSFER"
        string      to_bank_name                        "recipient bank e.g. Vietcombank"
        string      to_account_number                   "recipient account number"
        string      to_account_name                     "recipient account name"
        string      refund_note                         "nullable · e.g. reason for refund"
        UUID        processed_by                        "ref users.id · STAFF/ADMIN who processed"
        timestamp   processed_at                        "NOT NULL"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
    }

    %% ═══════════════════════════════════════════════════════
    %% coach_db  (coach-service · PostgreSQL + Elasticsearch · port 3005)
    %% Owns: coach profiles, availability, enrollments (with payment), reviews
    %% ═══════════════════════════════════════════════════════
    coaches {
        UUID        id                          PK
        UUID        user_id                     UK      "ref users.id · 1 user = 1 coach profile"
        UUID        approved_by                         "ref users.id · nullable · ADMIN who approved"
        string      specialty                           "SINGLES|DOUBLES|FOOTWORK|SMASH|DEFENSE"
        decimal     hourly_rate                         "NOT NULL · VND"
        decimal     rating                              "DECIMAL(3,2) DEFAULT 0.00 · avg of coach_reviews"
        int         total_reviews                       "DEFAULT 0 · denormalized counter"
        text        bio                                 "nullable"
        json        certifications                      "nullable · array of cert objects"
        string      status                              "PENDING_APPROVAL|ACTIVE|SUSPENDED"
        timestamp   approved_at                         "nullable"
        timestamp   suspended_at                        "nullable"
        timestamp   deleted_at                          "nullable · soft delete"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    coach_schedules {
        UUID        id                          PK
        UUID        coach_id                    FK      "→ coaches.id"
        string      day_of_week                         "MONDAY|TUESDAY|...|SUNDAY"
        time        available_from                      "NOT NULL"
        time        available_to                        "NOT NULL"
    }
    coach_enrollments {
        UUID        id                          PK
        UUID        coach_id                    FK      "→ coaches.id"
        UUID        user_id                             "ref users.id · student · cross-service UUID"
        UUID        court_id                            "ref courts.id (1 sân học) · nullable · cross-service UUID"
        UUID        payment_id                          "ref payments.id · nullable · cross-service UUID"
        UUID        cancelled_by                        "ref users.id · nullable"
        decimal     total_paid                          "nullable · VND charged"
        decimal     refund_amount                       "nullable · VND refunded on cancel"
        string      status                              "PENDING|CONFIRMED|CANCELLED|COMPLETED"
        string      cancel_reason                       "nullable"
        timestamp   cancelled_at                        "nullable"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    enrollment_slots {
        UUID        enrollment_id               FK      "→ coach_enrollments.id"
        UUID        slot_id                     UK      "ref time_slots.id · cross-service UUID · 1 ô 30' buổi học giữ · UNIQUE (1 slot ↔ 1 enrollment)"
    }
    coach_reviews {
        UUID        id                          PK
        UUID        coach_id                    FK      "→ coaches.id"
        UUID        user_id                             "ref users.id · reviewer · cross-service UUID"
        UUID        enrollment_id                       "ref coach_enrollments.id · 1 enrollment = 1 review"
        int         rating                              "CHECK (rating BETWEEN 1 AND 5)"
        text        comment                             "nullable"
        boolean     is_flagged                          "DEFAULT FALSE"
        UUID        flagged_by                          "ref users.id · nullable · STAFF/ADMIN"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
    }

    %% ═══════════════════════════════════════════════════════
    %% notification_db  (notification-service · MongoDB · port 3008)
    %% Owns: templates, delivery history, read status
    %% ═══════════════════════════════════════════════════════
    notification_templates {
        string      event_type                  PK      "SLOT_JOINED|PAYMENT_CONFIRMED|MATCH_CANCELLED|EMAIL_VERIFY|PASSWORD_RESET|BOOKING_RECEIPT|ESCROW_REIMBURSED|COACH_ENROLLED"
        string      title_template                      "Handlebars: Hello {{userName}}"
        string      body_template                       "Handlebars: Court {{courtName}} at {{time}}"
        string      channel                             "EMAIL|PUSH|SMS"
        boolean     is_active                           "DEFAULT TRUE · can disable without code deploy"
        timestamp   updated_at
    }
    notification_history {
        UUID        id                          PK
        string      event_type                          "mirrors notification_templates.event_type"
        UUID        user_id                             "ref users.id · recipient"
        UUID        match_id                            "ref matches.id · nullable"
        UUID        booking_id                          "ref bookings.id · nullable"
        UUID        enrollment_id                       "ref coach_enrollments.id · nullable"
        string      channel                             "EMAIL|PUSH|SMS"
        string      rendered_title
        string      rendered_body
        string      status                              "SENT|FAILED"
        int         retry_count                         "DEFAULT 0"
        boolean     is_read                             "DEFAULT FALSE · for in-app notification badge"
        timestamp   read_at                             "nullable · set when user marks as read"
        timestamp   created_at                          "NOT NULL"
        timestamp   sent_at                             "nullable"
    }

    %% ═══════════════════════════════════════════════════════
    %% event_db  (event-service · PostgreSQL · port 3009)
    %% Owns: social/competitive events, ticket sales
    %% ═══════════════════════════════════════════════════════
    events {
        UUID        id                          PK
        UUID        club_id                             "ref clubs.id · cross-service UUID · sự kiện ở 1 CLB"
        UUID        created_by                          "ref users.id · STAFF or ADMIN"
        int         event_number                UK      "SERIAL · display ID e.g. #2748"
        string      title                               "NOT NULL e.g. [Xé vé] - SOCIAL"
        string      format                              "SOCIAL|COMPETITIVE"
        string      ticket_type                         "XE_VE"
        string      sport                               "PICKLEBALL|BADMINTON"
        decimal     skill_min                           "DECIMAL(3,1) · DUPR min e.g. 1.0"
        decimal     skill_max                           "DECIMAL(3,1) · DUPR max e.g. 2.5"
        date        event_date                          "NOT NULL"
        time        start_time                          "NOT NULL"
        time        end_time                            "NOT NULL"
        text        courts_involved                     "JSON array of court names"
        int         total_tickets                       "NOT NULL"
        int         tickets_sold                        "NOT NULL DEFAULT 0"
        decimal     price_per_ticket                    "NOT NULL · VND"
        string      status                              "OPEN|FULL|CANCELLED|COMPLETED"
        timestamp   created_at                          "NOT NULL DEFAULT NOW()"
        timestamp   updated_at                          "NOT NULL DEFAULT NOW()"
    }
    event_tickets {
        UUID        id                          PK
        UUID        event_id                    FK      "→ events.id"
        UUID        user_id                             "ref users.id · buyer · cross-service UUID"
        UUID        payment_id                          "ref payments.id · cross-service UUID"
        int         quantity                            "NOT NULL DEFAULT 1"
        decimal     total_paid                          "NOT NULL · VND"
        string      status                              "PENDING|CONFIRMED|CANCELLED|REFUNDED"
        timestamp   purchased_at                        "NOT NULL DEFAULT NOW()"
        timestamp   cancelled_at                        "nullable"
    }

    %% ══════════════════════════════════════════════════════
    %%  INTRA-SERVICE RELATIONSHIPS
    %%  (enforced FK constraints within same database)
    %% ══════════════════════════════════════════════════════

    %% user_db
    users               ||--o{    user_roles              : "has roles"
    roles               ||--o{    user_roles              : "assigned via"
    users               ||--o{    audit_logs              : "performed actions"

    %% court_db
    clubs               ||--o{    courts                  : "has courts (Sân 1..N)"
    clubs               ||--o{    court_pricing_rules     : "has pricing rules"
    clubs               ||--o{    club_reviews            : "receives reviews"
    courts              ||--o{    time_slots              : "has slots"

    %% booking_db
    bookings            ||--o{    booking_items           : "has items (1 ô 30')"

    %% matchmaking_db
    matches             ||--o{    match_slots             : "reserves slots"
    matches             ||--o{    match_participants      : "has players"
    matches             ||--o{    match_waitlist          : "has waiting list"
    matches             ||--o{    outbox_events           : "publishes events"

    %% escrow_db
    escrow_accounts     ||--o{    escrow_transactions     : "has transactions"

    %% coach_db
    coaches             ||--o{    coach_schedules         : "has availability"
    coaches             ||--o{    coach_enrollments       : "has enrollments"
    coaches             ||--o{    coach_reviews           : "receives reviews"
    coach_enrollments   ||--o{    enrollment_slots        : "reserves slots"

    %% event_db
    events              ||--o{    event_tickets           : "sold as tickets"

    %% ══════════════════════════════════════════════════════
    %%  CROSS-SERVICE LOGICAL LINKS
    %%  (UUID-only references · enforced via Kafka + Saga)
    %% ══════════════════════════════════════════════════════

    %% users ↔ all services
    users               }o..o{    bookings                : "places booking (cross-DB)"
    users               }o..o{    matches                 : "hosts match (cross-DB)"
    users               }o..o{    match_participants      : "joins match (cross-DB)"
    users               }o..o{    match_waitlist          : "waits for slot (cross-DB)"
    users               }o..o{    coaches                 : "is a coach (cross-DB)"
    users               }o..o{    coach_enrollments       : "enrolls (cross-DB)"
    users               }o..o{    coach_reviews           : "writes review (cross-DB)"
    users               }o..o{    club_reviews            : "reviews club (cross-DB)"
    users               }o..o{    payments                : "pays (cross-DB)"
    users               }o..o{    escrow_accounts         : "receives settlement (cross-DB)"
    users               }o..o{    escrow_transactions     : "party in transaction (cross-DB)"
    users               }o..o{    event_tickets           : "buys ticket (cross-DB)"
    users               }o..o{    notification_history    : "receives notification (cross-DB)"

    %% clubs / courts ↔ services
    clubs               }o..o{    bookings                : "order at club (cross-DB)"
    clubs               }o..o{    matches                 : "hosts match (cross-DB)"
    clubs               }o..o{    events                  : "hosts event (cross-DB)"
    courts              }o..o{    booking_items           : "court of an item (cross-DB)"
    courts              }o..o{    matches                 : "match on court (cross-DB)"
    courts              }o..o{    coach_enrollments       : "court of a lesson (cross-DB)"

    %% time_slots ↔ services
    time_slots          }o..o{    match_slots             : "reserved by match (cross-DB)"
    time_slots          }o..o{    booking_items           : "reserved by booking item (cross-DB)"
    time_slots          }o..o{    enrollment_slots        : "reserved for coaching (cross-DB)"
    time_slots          }o..o{    events                  : "occupied by event (cross-DB)"

    %% bookings ↔ services
    bookings            }o..o{    payments                : "paid via Bank (cross-DB)"
    bookings            }o..o{    notification_history    : "triggers notification (cross-DB)"
    bookings            }o..o{    club_reviews            : "enables review (cross-DB)"

    %% matches ↔ services
    matches             }o..o{    payments                : "host pays court fee (cross-DB)"
    matches             }o..o{    escrow_accounts         : "has 1 escrow account (cross-DB)"
    matches             }o..o{    notification_history    : "triggers notification (cross-DB)"

    %% match_participants ↔ services
    match_participants  }o..o{    payments                : "player pays slot fee (cross-DB)"

    %% escrow ↔ services
    escrow_transactions }o..o{    payments                : "references bank payment (cross-DB)"

    %% coach enrollments ↔ services
    coach_enrollments   }o..o{    payments                : "paid via Bank QR (cross-DB)"
    coach_enrollments   }o..o{    notification_history    : "triggers notification (cross-DB)"

    %% events ↔ services
    event_tickets       }o..o{    payments                : "paid via Bank QR (cross-DB)"
    events              }o..o{    notification_history    : "triggers notification (cross-DB)"
```

---

## Legend

| Symbol | Meaning |
|---|---|
| `PK` | Primary Key |
| `UK` | Unique Constraint |
| `FK` | Foreign Key — **same database only** |
| `||--o{` | One-to-Many — enforced FK, same DB |
| `}o..o{` | Cross-service logical link — UUID ref only, **no DB-level FK** · enforced via Kafka + Saga Pattern |

---

## Services → Databases

| Service | Database | Engine | Port | Key Responsibility |
|---|---|---|---|---|
| `user-service` | `user_db` | PostgreSQL | 3001 | Auth · JWT · OAuth2 · Refresh Token · Email Verify · Audit Log |
| `court-service` | `court_db` | PostgreSQL | 3002 | Clubs (venue) · Courts (Sân) · Pricing Rules · Slots · Slot Auto-Gen · Club Reviews · Geo Search |
| `booking-service` | `booking_db` | PostgreSQL | 3003 | Court Bookings (header + items · multi-slot order) · Cancellation Policy · Idempotency Guard |
| `matchmaking-service` | `matchmaking_db` | PostgreSQL | 3004 | Matches · Saga · Outbox · Waiting List · Socket.io |
| `payment-service` | `payment_db` | PostgreSQL | 3006 | Bank QR · Proof Upload · STAFF Manual Confirm · Manual Refund |
| `escrow-service` | `escrow_db` | PostgreSQL | 3007 | Hold Court Fee · Reimburse Host · Settle Court Owner · Refund All |
| `coach-service` | `coach_db` | PostgreSQL + Elasticsearch | 3005 | Coach Profiles · Enrollment (with Payment) · Reviews |
| `notification-service` | `notification_db` | MongoDB | 3008 | SendGrid Email · FCM Push · DLQ · Read Status |
| `event-service` | `event_db` | PostgreSQL | 3009 | Social/Competitive Events · Ticket Sales |

---

## Payment Flow (Bank QR + Manual Confirm)

> Reference UI: `datlich.alobo.vn/sportPaymentScreen`

```
USER clicks Pay / Join / Enroll / Buy Ticket
        │
        ▼
POST /api/payments/initiate
  → payment created: status=PENDING, expires_at=NOW()+10min
  → Redis: payment:countdown:{paymentId} TTL 10min
  → slot locked: lock:slot:{slotId}:match_create TTL 10min
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│              PAYMENT SCREEN (frontend)                        │
│  1. Tài khoản ngân hàng:                                     │
│     Tên TK:    Trần Quốc Phú                                 │
│     Số TK:     0962728894                                    │
│     Ngân hàng: Shinhan Bank VN                               │
│     [QR Code image from bank_accounts.qr_image_url]          │
│  2. ⚠️ Chuyển khoản 100,000đ · nội dung: #184              │
│  3. Đơn còn được giữ trong: ⏱ 09:59 (countdown)            │
│  4. [Upload zone: Nhấn vào để tải hình thanh toán (*)]       │
│  5. [XÁC NHẬN ĐẶT] button (enabled after image uploaded)    │
└──────────────────────────────────────────────────────────────┘
        │
        ▼
USER transfers money + takes screenshot
        │
        ▼
POST /api/payments/{id}/proof  (multipart image upload)
  → Cloudinary upload → payment_proofs row created
  → payment.status = PROOF_SUBMITTED
  → Kafka: payment.proof.submitted → notification-service
  → STAFF receives push/email: "New proof #184 awaiting review"
        │
        ▼
STAFF reviews in Admin Panel (/admin/payments)
  → Sees proof image + booking info + transfer note
  → Checks bank statement
        │
   ┌────┴────┐
   ▼         ▼
CONFIRM   REJECT
   │         │
   │         └─ payment.status = EXPIRED
   │           → slot released, match CANCELLED
   │           → user notified: "Proof rejected, please re-book"
   │
   └─ payment.status = CONFIRMED
     → Kafka: payment.host.confirmed / payment.player.confirmed
     → escrow-service records deposit/reimbursement
     → match.status → OPEN (if Host) or participant added (if Player)
     → User notified: "Booking confirmed! 🎉"

SCHEDULER (every 1 min):
  → Finds PENDING payments past expires_at → set EXPIRED
  → Releases slot lock, notifies user: "Đã hết thời gian giữ chỗ"
```

---

## Cancellation Policy (Court Booking & Match)

| When | Who Cancels | Player Refund | Host / Court Net |
|---|---|---|---|
| > 24h before match | Player self | 100% `price_per_person` | Host keeps reimbursement received |
| 2h – 24h before match | Player self | 50% `price_per_person` | Host reverses 50% of reimbursement |
| < 2h before match | Player self | 0% | Host keeps full reimbursement |
| Any time | Host cancels match | 100% all Players | Host refunded `court_price − Σreimbursements` |
| System timeout (no payment in 10 min) | Scheduler | N/A (no players) | Host refunded full `court_price` |
| Match COMPLETED | — | No refund | Court Owner receives full `court_price` via Escrow |

> **Court booking (đặt sân lẻ)**: mốc tính chính sách hoàn = `bookings.earliest_start_time` (ô bắt đầu sớm nhất trong đơn). Số tiền hoàn dựa trên `SUM(booking_items.price)` theo % ở trên.

---

## Key Business Rules Reflected in Schema

| Rule | Schema Evidence |
|---|---|
| Only `is_email_verified=TRUE` users can book/join | `users.is_email_verified` · enforced via `@PreAuthorize` |
| `court_price` is snapshotted at match creation | `matches.court_price` — immutable after creation |
| 1 CLB (venue) có nhiều sân vật lý | `clubs` ──< `courts` (FK `courts.club_id`) · "Sân N" = `courts.court_number` |
| 1 đơn đặt = N ô 30' (nhiều sân) · 1 thanh toán | `bookings` (header) ──< `booking_items` (1 ô 30' / row) · `time_slots.booking_id` chung header |
| Giá theo (CLB, môn, loại ngày, khung giờ, loại khách) | `court_pricing_rules` (unique club+sport+day_type+start_time+customer_type) · snapshot vào `booking_items.price` |
| Match / buổi học cũng giữ nhiều ô 30' | `match_slots`, `enrollment_slots` (N slot / 1 match·enrollment) |
| 1 booking → 1 club review | `club_reviews.booking_id UNIQUE` constraint |
| 1 enrollment → 1 coach review | `coach_reviews.enrollment_id` — enforced at service layer |
| Host auto-joins as first participant | `match_participants` row with `host_id` created on `OPEN` |
| Free match (price=0) skips payment step | `match_participants.payment_id` is nullable |
| Slot locked during Host payment (10 min) | Redis `lock:slot:{slotId}:match_create` TTL 10m + `time_slots.match_id` ref |
| Booking nhiều ô → khoá TẤT CẢ ô trong 1 transaction | Redis `lock:slot:{slotId}` cho mỗi `booking_items.slot_id`; 1 ô fail → rollback cả đơn |
| Outbox table cleaned after 30 days | `outbox_events.sent_at` — scheduler deletes old SENT rows |
| `processed_events` cleaned after 7 days | `booking_processed_events.processed_at` — scheduler deletes old rows |
| Soft delete (users, coaches) | `users.deleted_at`, `coaches.deleted_at` — never hard delete |
| Audit trail for all admin actions | `audit_logs` table with before/after JSON snapshots |
