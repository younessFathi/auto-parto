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

    private static final String COLUMNS = """
            pr.public_id, au.public_id AS buyer_public_id, pr.name, pr.description,
            pr.condition, pr.category, pr.vehicle_make, pr.vehicle_model, pr.vehicle_year,
            pr.status, pr.view_count, pr.created_at, pr.updated_at, pr.version
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
        return create(buyerUserPublicId, name, description, condition, category, null, null, null);
    }

    @Override
    public Mono<PartRequestRecord> create(UUID buyerUserPublicId, String name, String description,
                                          String condition, String category, String vehicleMake,
                                          String vehicleModel, Integer vehicleYear) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        requireText(name, "name");
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql("""
                        INSERT INTO part_requests
                            (public_id, buyer_user_id, buyer_role, name, description, condition,
                             category, vehicle_make, vehicle_model, vehicle_year)
                        SELECT gen_random_uuid(), au.id, 'BUYER', :name, :description, :condition,
                               :category, :vehicleMake, :vehicleModel, :vehicleYear
                        FROM app_users au
                        WHERE au.public_id = :buyerPublicId AND au.role = 'BUYER' AND au.status = 'ACTIVE'
                        RETURNING public_id
                        """)
                .bind("buyerPublicId", buyerUserPublicId)
                .bind("name", name);
        insert = bindNullable(insert, "description", description, String.class);
        insert = bindNullable(insert, "condition", condition, String.class);
        insert = bindNullable(insert, "category", category, String.class);
        insert = bindNullable(insert, "vehicleMake", vehicleMake, String.class);
        insert = bindNullable(insert, "vehicleModel", vehicleModel, String.class);
        insert = bindNullable(insert, "vehicleYear", vehicleYear, Integer.class);
        return transactions.transactional(insert
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one())
                .flatMap(this::findByPublicId);
    }

    @Override
    public Mono<PartRequestRecord> findByPublicId(UUID publicId) {
        requirePublicId(publicId, "publicId");
        return databaseClient.sql(("SELECT %s FROM part_requests pr JOIN app_users au ON au.id = pr.buyer_user_id "
                        + "WHERE pr.public_id = :publicId").formatted(COLUMNS))
                .bind("publicId", publicId)
                .map((row, metadata) -> map(row))
                .one();
    }

    @Override
    public Flux<PartRequestRecord> findByBuyerPublicId(UUID buyerUserPublicId, int page, int size) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        requirePage(page, size);
        return databaseClient.sql(("SELECT %s FROM part_requests pr JOIN app_users au ON au.id = pr.buyer_user_id "
                        + "WHERE au.public_id = :buyerPublicId ORDER BY pr.created_at DESC, pr.id DESC "
                        + "LIMIT :size OFFSET :offset").formatted(COLUMNS))
                .bind("buyerPublicId", buyerUserPublicId)
                .bind("size", size)
                .bind("offset", (long) page * size)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Flux<PartRequestRecord> findByBuyerUserPublicId(UUID buyerUserPublicId,
                                                           Instant cursorCreatedAt,
                                                           UUID cursorPublicId,
                                                           int limit) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        if ((cursorCreatedAt == null) != (cursorPublicId == null)) {
            throw new IllegalArgumentException("cursor timestamp and public ID must be supplied together");
        }
        String base = ("SELECT %s FROM part_requests pr JOIN app_users au ON au.id = pr.buyer_user_id "
                + "WHERE au.public_id = :buyerUserPublicId ").formatted(COLUMNS);
        String sql = cursorCreatedAt == null
                ? base + "ORDER BY pr.created_at DESC, pr.public_id DESC LIMIT :limit"
                : base + "AND (pr.created_at < :cursorCreatedAt OR "
                + "(pr.created_at = :cursorCreatedAt AND pr.public_id < :cursorPublicId)) "
                + "ORDER BY pr.created_at DESC, pr.public_id DESC LIMIT :limit";
        DatabaseClient.GenericExecuteSpec query = databaseClient.sql(sql)
                .bind("buyerUserPublicId", buyerUserPublicId)
                .bind("limit", limit);
        if (cursorCreatedAt != null) {
            query = query.bind("cursorCreatedAt", cursorCreatedAt)
                    .bind("cursorPublicId", cursorPublicId);
        }
        return query.map((row, metadata) -> map(row)).all();
    }

    @Override
    public Mono<Long> countByBuyerPublicId(UUID buyerUserPublicId) {
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        return databaseClient.sql("SELECT count(*) AS total FROM part_requests pr "
                        + "JOIN app_users au ON au.id = pr.buyer_user_id WHERE au.public_id = :buyerPublicId")
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
        if (expectedVersion < 0 || updatedAt == null) {
            throw new IllegalArgumentException("valid expectedVersion and updatedAt are required");
        }
        return transactions.transactional(databaseClient.sql("""
                        UPDATE part_requests pr SET status = :status, version = pr.version + 1, updated_at = :updatedAt
                        FROM app_users au WHERE pr.public_id = :publicId AND au.id = pr.buyer_user_id
                          AND au.public_id = :buyerPublicId AND pr.version = :expectedVersion
                        RETURNING pr.public_id
                        """)
                .bind("status", status).bind("updatedAt", updatedAt).bind("publicId", publicId)
                .bind("buyerPublicId", buyerUserPublicId).bind("expectedVersion", expectedVersion)
                .map((row, metadata) -> row.get("public_id", UUID.class)).one())
                .flatMap(this::findByPublicId);
    }

    @Override
    public Mono<PartRequestRecord> transition(UUID publicId, UUID buyerUserPublicId, String status) {
        requirePublicId(publicId, "publicId");
        requirePublicId(buyerUserPublicId, "buyerUserPublicId");
        return databaseClient.sql("""
                        UPDATE part_requests pr SET status = :status, updated_at = CURRENT_TIMESTAMP,
                            version = pr.version + 1
                        FROM app_users au WHERE pr.public_id = :publicId AND au.id = pr.buyer_user_id
                          AND au.public_id = :buyerUserPublicId AND pr.status = 'OPEN'
                        RETURNING pr.public_id
                        """)
                .bind("publicId", publicId).bind("buyerUserPublicId", buyerUserPublicId).bind("status", status)
                .map((row, metadata) -> row.get("public_id", UUID.class)).one()
                .flatMap(this::findByPublicId);
    }

    private static PartRequestRecord map(io.r2dbc.spi.Row row) {
        String condition = row.get("condition", String.class);
        String category = row.get("category", String.class);
        return new PartRequestRecord(row.get("public_id", UUID.class), row.get("buyer_public_id", UUID.class),
                row.get("name", String.class), row.get("description", String.class),
                condition == null ? null : Condition.valueOf(condition),
                category == null ? null : Category.valueOf(category), row.get("status", String.class),
                row.get("view_count", Long.class), row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class), row.get("vehicle_make", String.class),
                row.get("vehicle_model", String.class), row.get("vehicle_year", Integer.class),
                row.get("version", Long.class));
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                       String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static void requirePublicId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
    }
}
