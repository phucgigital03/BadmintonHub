package com.badmintonhub.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * One document per message. Lives in Mongo {@code chat_db.messages}.
 *
 * <p>{@code _id} is a Mongo {@link ObjectId} on purpose (P0-4 · §E.4/§F.4): it is monotonic in creation
 * time, so it doubles as the <b>keyset pagination cursor</b> ({@code find _id < before sort _id desc}).
 * A UUID id would break that cursor.</p>
 *
 * <p>Delivery is a 3-state derived from timestamps (no status column, §B): {@code sent}=has
 * {@code createdAt}, {@code delivered}=has {@code deliveredAt}, {@code read}=has {@code readAt}.
 * {@code clientMsgId} + a unique index on {@code (conversationId, senderId, clientMsgId)} make sends
 * idempotent (D.6 · P0-5).</p>
 */
@Document("messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private ObjectId id;

    /** ref conversations.id · indexed for per-thread pagination. */
    private UUID conversationId;

    private UUID senderId;
    private SenderRole senderRole;

    /** Snapshot of the sender's display name (Extended Reference, §F.3). */
    private String senderName;

    private MessageType type;

    /** Text body, or the caption for an IMAGE message. */
    private String content;

    /** Cloudinary URL (or {@code local-fallback://}) — only for IMAGE messages (PHASE 3). */
    private String imageUrl;

    /** Client-generated idempotency key (unique per conversation+sender). */
    private String clientMsgId;

    /** {@code sent → delivered} mark: set when the recipient's client ACKs the frame (PHASE 3). */
    private Instant deliveredAt;

    /** {@code delivered → read} mark: set when the recipient opens the thread. */
    private Instant readAt;

    /** The {@code sent} mark (server receive time). Indexed with _id for time-ordered reads. */
    private Instant createdAt;
}
