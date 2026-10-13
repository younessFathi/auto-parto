package auto.parto.persistence;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.marketplace", name = "enabled",
        havingValue = "true")
public class R2dbcPartRequestRepository implements PartRequestRepository {

    private static final String SELECT = """
            SELECT pr.public_id, au.public_id AS buyer_public_id, pr.name, pr.description,
                   pr.condition, pr.category, pr.status, pr.view_count, pr.created_at, pr.updated_at
            FROM part_requests pr
            JOIN app_users au ON au.id = pr.buyer_user_id
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcPartRequestRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<PartRequestRecord> create(UUID buyerUserPublicId, String name, String description,
                                          String condition, String category) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        requireText(name, "name");
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql("""
                        INSERT INTO part_requests (public_id, buyer_user_id, name, description, condition, category)
                        SELECT gen_random_uuid(), id, :name, :description, :condition, :category
                        FROM app_users
                        WHERE public_id = :buyerPublicId AND role = 'BUYER' AND status = 'ACTIVE'
                        RETURNING public_id
                        """)
                .bind("buyerPublicId", buyerUserPublicId)
                .bind("name", name);
        insert = bindNullable(insert, "description", description);
        insert = bindNullable(insert, "condition", condition);
        insert = bindNullable(insert, "category", category);
        return transactions.transactional(insert
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one())
                .flatMap(this::findByPublicId);
    }

    @Override
    public Mono<PartRequestRecord> findByPublicId(UUID publicId) {
        requirePublicId(publicId, "publicId");
        return databaseClient.sql(SELECT + " WHERE pr.public_id = :publicId")
                .bind("publicId", publicId)
                .map((row, metadata) -> map(row))
                .one();
    }

    @Override
    public Flux<PartRequestRecord> findByBuyerPublicId(UUID buyerUserPublicId, int page, int size) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        requirePage(page, size);
        return databaseClient.sql(SELECT + """
                        WHERE au.public_id = :buyerPublicId
                        ORDER BY pr.created_at DESC, pr.id DESC
                        LIMIT :size OFFSET :offset
                        """)
                .bind("buyerPublicId", buyerUserPublicId)
                .bind("size", size)
                .bind("offset", (long) page * size)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<Long> countByBuyerPublicId(UUID buyerUserPublicId) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        return databaseClient.sql("""
                        SELECT count(*) AS total
                        FROM part_requests pr
                        JOIN app_users au ON au.id = pr.buyer_user_id
                        WHERE au.public_id = :buyerPublicId
                        """)
                .bind("buyerPublicId", buyerUserPublicId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one();
    }

    @Override
    public Mono<PartRequestRecord> updateStatus(UUID publicId, UUID buyerUserPublicId, String status,
                                                long expectedVersion, Instant updatedAt) {
        requirePublicId(publicId, "publicId");
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        requireText(status, "status");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must be >= 0");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt is required");
        return transactions.transactional(databaseClient.sql("""
                        UPDATE part_requests pr
                        SET status = :status, version = pr.version + 1, updated_at = :updatedAt
                        FROM app_users au
                        WHERE pr.public_id = :publicId
                          AND au.id = pr.buyer_user_id
                          AND au.public_id = :buyerPublicId
                          AND pr.version = :expectedVersion
                        RETURNING pr.public_id
                        """)
                .bind("status", status)
                .bind("updatedAt", updatedAt)
                .bind("publicId", publicId)
                .bind("buyerPublicId", buyerUserPublicId)
                .bind("expectedVersion", expectedVersion)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one())
                .flatMap(this::findByPublicId);
    }

    private static PartRequestRecord map(io.r2dbc.spi.Row row) {
        return new PartRequestRecord(
                row.get("public_id", UUID.class),
                row.get("buyer_public_id", UUID.class),
                row.get("name", String.class),
                row.get("description", String.class),
                enumValue(row.get("condition", String.class), Condition.class),
                enumValue(row.get("category", String.class), Category.class),
                row.get("status", String.class),
                row.get("view_count", Long.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static void requirePublicId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                  String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static void requirePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
}
