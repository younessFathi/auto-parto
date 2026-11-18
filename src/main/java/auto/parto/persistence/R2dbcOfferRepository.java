package auto.parto.persistence;

import auto.parto.enums.Currency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.marketplace", name = "enabled",
        havingValue = "true")
public class R2dbcOfferRepository implements OfferRepository {

    private static final String SELECT = """
            SELECT offer.public_id,
                   request.public_id AS request_public_id,
                   listing.public_id AS listing_public_id,
                   buyer.public_id AS buyer_public_id,
                   seller.public_id AS seller_public_id,
                   offer.amount_minor, offer.currency, offer.status, offer.negotiable,
                   offer.delivery_amount_minor, offer.delivery_mode,
                   offer.created_at, offer.updated_at, offer.version, acceptance.public_id AS acceptance_public_id
            FROM offers offer
            JOIN app_users buyer ON buyer.id = offer.buyer_user_id
            JOIN app_users seller ON seller.id = offer.seller_user_id
            LEFT JOIN part_requests request ON request.id = offer.part_request_id
            LEFT JOIN part_listings listing ON listing.id = offer.part_listing_id
            LEFT JOIN offer_acceptances acceptance ON acceptance.offer_id = offer.id
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcOfferRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<OfferRecord> create(UUID buyerUserPublicId, UUID sellerUserPublicId,
                                    UUID partRequestPublicId, UUID partListingPublicId,
                                    long amountMinor, String currency, boolean negotiable,
                                    long deliveryAmountMinor, String deliveryMode) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        requirePublicId(sellerUserPublicId, "sellerUserPublicId");
        if (partRequestPublicId == null && partListingPublicId == null) {
            throw new IllegalArgumentException("an offer target is required");
        }
        if (partRequestPublicId != null && partListingPublicId != null) {
            throw new IllegalArgumentException("an offer cannot have two targets");
        }
        if (amountMinor < 0) throw new IllegalArgumentException("amountMinor must be >= 0");
        if (deliveryAmountMinor < 0) throw new IllegalArgumentException("deliveryAmountMinor must be >= 0");
        requireCurrency(currency);
        Mono<OfferRecord> created = partRequestPublicId != null
                ? createForRequest(sellerUserPublicId, partRequestPublicId, amountMinor, currency,
                        negotiable, deliveryAmountMinor, deliveryMode)
                : createForListing(buyerUserPublicId, partListingPublicId, amountMinor, currency,
                        negotiable, deliveryAmountMinor, deliveryMode);
        return created.flatMap(offer -> buyerUserPublicId.equals(offer.buyerUserPublicId())
                        && sellerUserPublicId.equals(offer.sellerUserPublicId())
                ? Mono.just(offer) : conflict("Offer participants do not match target ownership"))
                .as(transactions::transactional);
    }

    @Override
    public Mono<OfferRecord> createForRequest(UUID sellerUserPublicId, UUID requestPublicId,
                                              long amountMinor, String currency, boolean negotiable,
                                              long deliveryAmountMinor, String deliveryMode) {
        requirePublicId(sellerUserPublicId, "sellerUserPublicId");
        requirePublicId(requestPublicId, "requestPublicId");
        validateAmounts(amountMinor, deliveryAmountMinor);
        requireCurrency(currency);
        return createFromTarget("part_request_id", "part_requests", requestPublicId, sellerUserPublicId,
                null, amountMinor, currency, negotiable, deliveryAmountMinor, deliveryMode);
    }

    @Override
    public Mono<OfferRecord> createForListing(UUID buyerUserPublicId, UUID listingPublicId,
                                              long amountMinor, String currency, boolean negotiable,
                                              long deliveryAmountMinor, String deliveryMode) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        requirePublicId(listingPublicId, "listingPublicId");
        validateAmounts(amountMinor, deliveryAmountMinor);
        requireCurrency(currency);
        return createFromTarget("part_listing_id", "part_listings", listingPublicId, buyerUserPublicId,
                buyerUserPublicId, amountMinor, currency, negotiable, deliveryAmountMinor, deliveryMode);
    }

    private Mono<OfferRecord> createFromTarget(String targetColumn, String targetTable, UUID targetPublicId,
                                                UUID actorPublicId, UUID buyerPublicId, long amountMinor,
                                                String currency, boolean negotiable, long deliveryAmountMinor,
                                                String deliveryMode) {
        boolean requestTarget = "part_request_id".equals(targetColumn);
        String targetAlias = requestTarget ? "request" : "listing";
        String buyerJoin = requestTarget ? "buyer.id = request.buyer_user_id" : "buyer.public_id = :buyerPublicId";
        String sellerJoin = requestTarget ? "seller.public_id = :actorPublicId" : "seller.id = listing.seller_user_id";
        String sql = """
                INSERT INTO offers (public_id, %s, buyer_user_id, seller_user_id, amount_minor, currency,
                                    negotiable, delivery_amount_minor, delivery_mode)
                SELECT gen_random_uuid(), %s.id, buyer.id, seller.id, :amountMinor, :currency,
                       :negotiable, :deliveryAmountMinor, :deliveryMode
                FROM %s %s
                JOIN app_users buyer ON %s
                JOIN app_users seller ON %s
                WHERE %s.public_id = :targetPublicId
                  AND buyer.role = 'BUYER' AND buyer.status = 'ACTIVE'
                  AND seller.role = 'SELLER' AND seller.status = 'ACTIVE'
                RETURNING public_id
                """.formatted(targetColumn, targetAlias, targetTable, targetAlias, buyerJoin, sellerJoin, targetAlias);
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql(sql)
                .bind("targetPublicId", targetPublicId)
                .bind("amountMinor", amountMinor)
                .bind("currency", currency)
                .bind("negotiable", negotiable)
                .bind("deliveryAmountMinor", deliveryAmountMinor);
        if (requestTarget) {
            insert = insert.bind("actorPublicId", actorPublicId);
        } else {
            insert = insert.bind("buyerPublicId", buyerPublicId);
        }
        insert = bindNullable(insert, "deliveryMode", deliveryMode, String.class);
        var statement = insert;
        return lockTarget(targetTable, targetPublicId)
                .flatMap(status -> (requestTarget ? "OPEN" : "PUBLISHED").equals(status)
                        ? statement.map((row, metadata) -> row.get("public_id", UUID.class)).one()
                                .flatMap(this::findByPublicId)
                        : conflict("Offer target is not available"))
                .as(transactions::transactional);
    }

    @Override
    public Mono<OfferRecord> findByPublicId(UUID publicId) {
        requirePublicId(publicId, "publicId");
        return databaseClient.sql(SELECT + " WHERE offer.public_id = :publicId")
                .bind("publicId", publicId)
                .map((row, metadata) -> map(row))
                .one();
    }

    @Override
    public Mono<OfferRecord> updateStatus(UUID publicId, UUID actorUserPublicId, String status,
                                          long expectedVersion, Instant updatedAt) {
        requirePublicId(publicId, "publicId");
        requirePublicId(actorUserPublicId, "actorUserPublicId");
        requireText(status, "status");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must be >= 0");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt is required");
        if ("ACCEPTED".equals(status)) {
            return accept(publicId, actorUserPublicId, expectedVersion, updatedAt);
        }
        return transactions.transactional(databaseClient.sql("""
                        UPDATE offers offer
                        SET status = :status, version = offer.version + 1, updated_at = :updatedAt
                        FROM app_users buyer, app_users seller
                        WHERE offer.public_id = :publicId
                          AND buyer.id = offer.buyer_user_id
                          AND seller.id = offer.seller_user_id
                          AND :actorUserPublicId IN (buyer.public_id, seller.public_id)
                          AND offer.version = :expectedVersion AND offer.status = 'PENDING'
                          AND buyer.status = 'ACTIVE' AND seller.status = 'ACTIVE'
                          AND (:status = 'CANCELLED' OR
                               (:status IN ('ACCEPTED', 'REJECTED') AND
                                :actorUserPublicId = CASE WHEN offer.part_request_id IS NOT NULL
                                    THEN buyer.public_id ELSE seller.public_id END))
                        RETURNING offer.public_id
                        """)
                .bind("status", status)
                .bind("updatedAt", updatedAt)
                .bind("publicId", publicId)
                .bind("actorUserPublicId", actorUserPublicId)
                .bind("expectedVersion", expectedVersion)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one())
                .flatMap(this::findByPublicId);
    }

    // Creation and acceptance always lock the shared target before any offer row.
    private Mono<String> lockTarget(String table, UUID publicId) {
        if (!java.util.Set.of("part_requests", "part_listings").contains(table)) {
            throw new IllegalArgumentException("unsupported target");
        }
        return databaseClient.sql("SELECT status FROM " + table + " WHERE public_id = :id FOR UPDATE")
                .bind("id", publicId).map((row, metadata) -> row.get("status", String.class)).one();
    }

    private Mono<OfferRecord> accept(UUID id, UUID actor, long expectedVersion, Instant now) {
        return findByPublicId(id).flatMap(initial -> {
            boolean request = initial.partRequestPublicId() != null;
            String table = request ? "part_requests" : "part_listings";
            UUID target = request ? initial.partRequestPublicId() : initial.partListingPublicId();
            return lockTarget(table, target).flatMap(targetStatus -> databaseClient
                    .sql(SELECT + " WHERE offer.public_id = :id FOR UPDATE OF offer")
                    .bind("id", id).map((row, metadata) -> map(row)).one()
                    .flatMap(offer -> verifyRecipient(offer, actor).then(Mono.defer(() -> {
                        if ("ACCEPTED".equals(offer.status())) {
                            return databaseClient.sql("""
                                    SELECT acceptance.public_id FROM offer_acceptances acceptance
                                    JOIN offers offer ON offer.id = acceptance.offer_id
                                    WHERE offer.public_id = :id
                                        AND (:version = acceptance.original_version OR :version = offer.version)
                                    """).bind("id", id).bind("version", expectedVersion)
                                    .map((row, metadata) -> row.get("public_id", UUID.class)).one()
                                    .flatMap(receipt -> findByPublicId(id))
                                    .switchIfEmpty(conflict("Acceptance receipt unavailable or version is stale"));
                        }
                        if (!"PENDING".equals(offer.status()) || offer.version() != expectedVersion
                                || !(request ? "OPEN" : "PUBLISHED").equals(targetStatus)) {
                            return conflict("Offer or target changed before acceptance");
                        }
                        return applyAcceptance(offer, actor, table, target, now);
                    }))));
        }).as(transactions::transactional);
    }

    private Mono<Void> verifyRecipient(OfferRecord offer, UUID actor) {
        UUID recipient = offer.partRequestPublicId() != null ? offer.buyerUserPublicId() : offer.sellerUserPublicId();
        if (!recipient.equals(actor)) return Mono.error(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Only the offer recipient may decide"));
        return databaseClient.sql("""
                SELECT public_id FROM app_users
                WHERE (public_id = :buyer AND role = 'BUYER' OR public_id = :seller AND role = 'SELLER')
                    AND status = 'ACTIVE'
                ORDER BY public_id FOR SHARE
                """).bind("buyer", offer.buyerUserPublicId()).bind("seller", offer.sellerUserPublicId())
                .map((row, meta) -> row.get("public_id", UUID.class)).all().count()
                .flatMap(count -> count == 2 ? Mono.<Void>empty() : conflict("Offer participants are not active"));
    }

    private Mono<OfferRecord> applyAcceptance(OfferRecord offer, UUID actor, String table, UUID target, Instant now) {
        boolean request = offer.partRequestPublicId() != null;
        String targetColumn = request ? "part_request_id" : "part_listing_id";
        String targetUpdate = request ? "status = 'CLOSED'" : "status = 'RESERVED', business_status = 'RESERVED'";
        var notifications = new R2dbcNotificationRepository(databaseClient, transactions);
        return databaseClient.sql("UPDATE " + table + " SET " + targetUpdate
                        + ", version = version + 1 WHERE public_id = :id")
                .bind("id", target).fetch().rowsUpdated()
                .then(databaseClient.sql("""
                        UPDATE offers SET status = 'ACCEPTED', version = version + 1, updated_at = :now
                        WHERE public_id = :id AND status = 'PENDING' AND version = :version
                        """).bind("id", offer.publicId()).bind("now", now).bind("version", offer.version())
                        .fetch().rowsUpdated())
                .flatMap(updated -> updated == 1 ? Mono.just(updated) : conflict("Offer changed before acceptance"))
                .then(databaseClient.sql("""
                        UPDATE offers SET status = 'REJECTED', version = version + 1, updated_at = :now
                        WHERE %s = (SELECT id FROM %s WHERE public_id = :target)
                            AND public_id <> :id AND status = 'PENDING'
                        """.formatted(targetColumn, table))
                        .bind("target", target).bind("id", offer.publicId()).bind("now", now).fetch().rowsUpdated())
                // Sequential enqueues share this R2DBC transaction. Redis/providers are intentionally absent.
                .then(enqueueAcceptance(notifications, offer, offer.buyerUserPublicId(), now))
                .flatMap(buyerNotification -> enqueueAcceptance(notifications, offer, offer.sellerUserPublicId(), now)
                        .flatMap(sellerNotification -> databaseClient.sql("""
                                INSERT INTO offer_acceptances (public_id, offer_id, deciding_user_id, original_version,
                                    buyer_notification_id, seller_notification_id)
                                SELECT gen_random_uuid(), offer.id, actor.id, :version, buyer.id, seller.id
                                FROM offers offer CROSS JOIN app_users actor
                                CROSS JOIN notifications buyer CROSS JOIN notifications seller
                                WHERE offer.public_id = :offer AND actor.public_id = :actor
                                    AND buyer.public_id = :buyer AND seller.public_id = :seller
                                """).bind("offer", offer.publicId()).bind("actor", actor).bind("version", offer.version())
                                .bind("buyer", buyerNotification.publicId()).bind("seller", sellerNotification.publicId())
                                .fetch().rowsUpdated()
                                .flatMap(rows -> rows == 1 ? findByPublicId(offer.publicId())
                                        : conflict("Acceptance evidence could not be recorded"))));
    }

    private Mono<NotificationRecord> enqueueAcceptance(NotificationRepository notifications, OfferRecord offer,
                                                        UUID recipient, Instant now) {
        // All interpolated values are canonical UUIDs, an integer and constants; no user-controlled text.
        String payload = """
                {"id":"%s-%s","userId":"%s","type":"OFFER_ACCEPTED",
                 "message":"Offer accepted","sysTime":%d}
                """.formatted(offer.publicId(), recipient, recipient, now.toEpochMilli());
        return notifications.enqueue(recipient, "OFFER_ACCEPTED", payload)
                .switchIfEmpty(conflict("Participant notification intent could not be recorded"));
    }

    private static <T> Mono<T> conflict(String message) {
        return Mono.error(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, message));
    }

    private static OfferRecord map(io.r2dbc.spi.Row row) {
        return new OfferRecord(
                row.get("public_id", UUID.class),
                row.get("request_public_id", UUID.class),
                row.get("listing_public_id", UUID.class),
                row.get("buyer_public_id", UUID.class),
                row.get("seller_public_id", UUID.class),
                row.get("amount_minor", Long.class),
                row.get("currency", String.class),
                row.get("status", String.class),
                Boolean.TRUE.equals(row.get("negotiable", Boolean.class)),
                row.get("delivery_amount_minor", Long.class),
                row.get("delivery_mode", String.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class), row.get("version", Long.class),
                row.get("acceptance_public_id", UUID.class));
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static void requirePublicId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireCurrency(String value) {
        Currency.requireSupportedCode(value);
    }

    private static void validateAmounts(long amountMinor, long deliveryAmountMinor) {
        if (amountMinor < 0) throw new IllegalArgumentException("amountMinor must be >= 0");
        if (deliveryAmountMinor < 0) throw new IllegalArgumentException("deliveryAmountMinor must be >= 0");
    }
}
