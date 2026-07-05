package com.badmintonhub.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * One support thread per customer. Lives in Mongo {@code chat_db.conversations}.
 *
 * <p>The "unassigned queue" is {@code status == OPEN && assignedStaffId == null}; a staff member's
 * private inbox is {@code assignedStaffId == me}. There is intentionally <b>no</b> {@code activeCustomerId}
 * field — the "one open thread per customer" invariant is enforced by a <b>partial-unique index</b> on
 * {@code customerId} (status ∈ OPEN/ASSIGNED), created by {@code ChatIndexInitializer} (P0-3 · §F.2/§F.7).
 * Counters ({@code customerUnread}/{@code staffUnread}) and {@code lastMessage*} are denormalized for O(1)
 * inbox render (Computed pattern, §F.3).</p>
 */
@Document("conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private UUID id;

    /** ref users.id · cross-service UUID. Indexed for the customer-thread lookup. */
    private UUID customerId;

    /** Snapshot of the customer's display name (Extended Reference, §F.3) — no join to user-service. */
    private String customerName;

    /** ref users.id · owner of this inbox. {@code null} = unassigned (in the queue). */
    private UUID assignedStaffId;

    private ConversationStatus status;

    private String lastMessagePreview;
    private Instant lastMessageAt;

    private int customerUnread;
    private int staffUnread;

    /** {@code null} = active; set when a CLOSED thread is archived (§F.5 · backlog). */
    private Instant archivedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
