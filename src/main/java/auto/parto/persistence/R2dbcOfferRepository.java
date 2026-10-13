package auto.parto.persistence;

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
public final class R2dbcOfferRepository implements OfferRepository {

    private static final String SELECT = """
            SELECT offer.public_id,
                   request.public_id AS request_public_id,
                   listing.public_id AS listing_public_id,
                   buyer.public_id AS buyer_public_id,
                   seller.public_id AS seller_public_id,
                   offer.amount_minor, offer.currency, offer.status, offer.negotiable,
                   offer.delivery_amount_minor, offer.delivery_mode,
                   offer.created_at, offer.updated_at
            FROM offers offer
            JOIN app_users buyer ON buyer.id = offer.buyer_user_id
            JOIN app_users seller ON seller.id = offer.seller_user_id
            LEFT JOIN part_requests request ON request.id = offer.part_request_id
            LEFT JOIN part_listings listing ON listing.id = offer.part_listing_id
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
        requireText(currency, "currency");
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql("""
                        INSERT INTO offers (public_id, part_request_id, part_listing_id, buyer_user_id,
                                            seller_user_id, amount_minor, currency, negotiable,
                                            delivery_amount_minor, delivery_mode)
                        SELECT gen_random_uuid(), request.id, listing.id, buyer.id, seller.id,
                               :amountMinor, :currency, :negotiable, :deliveryAmountMinor, :deliveryMode
                        FROM app_users buyer
                        CROSS JOIN app_users seller
                        LEFT JOIN part_requests request ON request.public_id = :requestPublicId
                        LEFT JOIN part_listings listing ON listing.public_id = :listingPublicId
                        WHERE buyer.public_id = :buyerPublicId AND buyer.role = 'BUYER'
                          AND buyer.status = 'ACTIVE'
                          AND seller.public_id = :sellerPublicId AND seller.role = 'SELLER'
                          AND seller.status = 'ACTIVE'
                          AND num_nonnulls(request.id, listing.id) = 1
                        RETURNING public_id
                        """)
                .bind("buyerPublicId", buyerUserPublicId)
                .bind("sellerPublicId", sellerUserPublicId)
                .bind("amountMinor", amountMinor)
                .bind("currency", currency)
                .bind("negotiable", negotiable)
                .bind("deliveryAmountMinor", deliveryAmountMinor);
        insert = bindNullable(insert, "requestPublicId", partRequestPublicId, UUID.class);
        insert = bindNullable(insert, "listingPublicId", partListingPublicId, UUID.class);
        insert = bindNullable(insert, "deliveryMode", deliveryMode, String.class);
        return transactions.transactional(insert
                        .map((row, metadata) -> row.get("public_id", UUID.class))
                        .one())
                .flatMap(this::findByPublicId);
    }

    @Override
    public Mono<OfferRecord> createForRequest(UUID sellerUserPublicId, UUID requestPublicId,
                                              long amountMinor, String currency, boolean negotiable,
                                              long deliveryAmountMinor, String deliveryMode) {
        requirePublicId(sellerUserPublicId, "sellerUserPublicId");
        requirePublicId(requestPublicId, "requestPublicId");
        validateAmounts(amountMinor, deliveryAmountMinor);
        requireText(currency, "currency");
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
        requireText(currency, "currency");
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
        return transactions.transactional(insert
                        .map((row, metadata) -> row.get("public_id", UUID.class))
                        .one())
                .flatMap(this::findByPublicId);
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
        return transactions.transactional(databaseClient.sql("""
                        UPDATE offers offer
                        SET status = :status, version = offer.version + 1, updated_at = :updatedAt
                        FROM app_users buyer, app_users seller
                        WHERE offer.public_id = :publicId
                          AND buyer.id = offer.buyer_user_id
                          AND seller.id = offer.seller_user_id
                          AND :actorUserPublicId IN (buyer.public_id, seller.public_id)
                          AND offer.version = :expectedVersion
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
                row.get("updated_at", Instant.class));
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

    private static void validateAmounts(long amountMinor, long deliveryAmountMinor) {
        if (amountMinor < 0) throw new IllegalArgumentException("amountMinor must be >= 0");
        if (deliveryAmountMinor < 0) throw new IllegalArgumentException("deliveryAmountMinor must be >= 0");
    }
}
