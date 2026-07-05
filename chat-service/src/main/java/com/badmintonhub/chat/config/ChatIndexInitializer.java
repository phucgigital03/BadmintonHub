package com.badmintonhub.chat.config;

import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Creates the chat Mongo indexes at startup — the Mongo counterpart of
 * {@code PaymentIndexInitializer} (which is JdbcTemplate/Postgres and cannot be reused).
 *
 * <p><b>Why this must exist (P0-2 · §F.7):</b> the repo has no Mongo index precedent — no {@code @Indexed}/
 * {@code @CompoundIndex} and {@code spring.data.mongodb.auto-index-creation} stays off. Without this runner
 * the <em>unique</em> guards below simply would not exist, and the DB-level backstops for idempotent send
 * and one-open-thread-per-customer would silently vanish (duplicate messages + two threads per customer in
 * prod). {@code ensureIndex} is idempotent, so this is safe to run on every boot (including tests).</p>
 */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class ChatIndexInitializer implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        IndexOperations conv = mongoTemplate.indexOps(Conversation.class);
        // Staff private inbox: assignedStaffId == me, sorted by recency.
        conv.ensureIndex(new Index()
                .on("assignedStaffId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .on("lastMessageAt", Sort.Direction.DESC));
        // Unassigned queue: only OPEN + unassigned docs are indexed (small, cheap to maintain).
        conv.ensureIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .on("lastMessageAt", Sort.Direction.DESC)
                .partial(PartialIndexFilter.of(where("assignedStaffId").is(null).and("status").is("OPEN"))));
        // One OPEN/ASSIGNED thread per customer (partial-unique — P0-3). NOT a sparse activeCustomerId
        // field: sparse-null would let every CLOSED doc collide on null.
        conv.ensureIndex(new Index()
                .on("customerId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(where("status").in("OPEN", "ASSIGNED"))));

        IndexOperations msg = mongoTemplate.indexOps(Message.class);
        // Idempotent send: DB-level dedupe backstop (P0-5).
        msg.ensureIndex(new Index()
                .on("conversationId", Sort.Direction.ASC)
                .on("senderId", Sort.Direction.ASC)
                .on("clientMsgId", Sort.Direction.ASC)
                .unique());
        // Keyset pagination of a thread's history (§F.4).
        msg.ensureIndex(new Index()
                .on("conversationId", Sort.Direction.ASC)
                .on("_id", Sort.Direction.DESC));

        log.info("Ensured chat_db indexes (conversations x3, messages x2 — incl. unique dedupe + partial-unique 1-thread/customer)");
    }
}
