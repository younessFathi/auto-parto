package auto.parto.persistence;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.enums.Currency;
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
public class R2dbcPartListingRepository implements PartListingRepository {

    private static final String SELECT = """
            SELECT pl.public_id, au.public_id AS seller_public_id, pl.name, pl.description,
                   pl.vehicle_make, pl.vehicle_model, pl.vehicle_year,
                   pl.condition, pl.category, pl.amount_minor, pl.currency, pl.status,
                   pl.business_status, pl.delivery_status, pl.storage_status,
                   pl.view_count, pl.created_at, pl.updated_at, pl.version
            FROM part_listings pl
            JOIN app_users au ON au.id = pl.seller_user_id
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcPartListingRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<PartListingRecord> create(UUID sellerUserPublicId, String name, String description,
                                          String condition, String category, long amountMinor, String currency) {
        return create(sellerUserPublicId, name, description, condition, category, null, null, null,
                amountMinor, currency);
    }

    @Override
    public Mono<PartListingRecord> create(UUID sellerUserPublicId, String name, String description,
                                          String condition, String category, String vehicleMake,
                                          String vehicleModel, Integer vehicleYear, long amountMinor, String currency) {
        requirePublicId(sellerUserPublicId, "sellerUserPublicId");
        requireText(name, "name");
        if (amountMinor < 0) throw new IllegalArgumentException("amountMinor must be >= 0");
        requireCurrency(currency);
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql("""
                        INSERT INTO part_listings (public_id, seller_user_id, name, description, condition,
                                                   vehicle_make, vehicle_model, vehicle_year,
                                                   category, amount_minor, currency, business_status,
                                                   delivery_status, storage_status)
                        SELECT gen_random_uuid(), id, :name, :description, :condition,
                               :vehicleMake, :vehicleModel, :vehicleYear, :category,
                               :amountMinor, :currency, 'DRAFT', 'NOT_READY', 'NOT_STORED'
                        FROM app_users
                        WHERE public_id = :sellerPublicId AND role = 'SELLER' AND status = 'ACTIVE'
                        RETURNING public_id
                        """)
                .bind("sellerPublicId", sellerUserPublicId)
                .bind("name", name)
                .bind("amountMinor", amountMinor)
                .bind("currency", currency);
        insert = bindNullable(insert, "description", description);
        insert = bindNullable(insert, "condition", condition);
        insert = bindNullable(insert, "vehicleMake", vehicleMake);
        insert = bindNullable(insert, "vehicleModel", vehicleModel);
        insert = vehicleYear == null ? insert.bindNull("vehicleYear", Integer.class) : insert.bind("vehicleYear", vehicleYear);
        insert = bindNullable(insert, "category", category);
        return transactions.transactional(insert
                        .map((row, metadata) -> row.get("public_id", UUID.class))
                        .one())
                .flatMap(this::findByPublicId);
    }

    @Override
    public Mono<PartListingRecord> findByPublicId(UUID publicId) {
        requirePublicId(publicId, "publicId");
        return databaseClient.sql(SELECT + " WHERE pl.public_id = :publicId")
                .bind("publicId", publicId)
                .map((row, metadata) -> map(row))
                .one();
    }

    @Override
    public Flux<PartListingRecord> findMatches(PartRequestRecord request, int limit) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        DatabaseClient.GenericExecuteSpec query = databaseClient.sql(SELECT + """
                        WHERE pl.status = 'PUBLISHED'
                          AND (:category IS NULL OR pl.category = :category)
                          AND (:condition IS NULL OR pl.condition = :condition)
                          AND (:vehicleMake IS NULL OR LOWER(pl.vehicle_make) = LOWER(:vehicleMake))
                          AND (:vehicleModel IS NULL OR LOWER(pl.vehicle_model) = LOWER(:vehicleModel))
                          AND (:vehicleYear IS NULL OR pl.vehicle_year = :vehicleYear)
                        ORDER BY
                          (CASE WHEN :condition IS NOT NULL AND pl.condition = :condition THEN 1 ELSE 0 END
                           + CASE WHEN :vehicleMake IS NOT NULL AND LOWER(pl.vehicle_make) = LOWER(:vehicleMake) THEN 1 ELSE 0 END
                           + CASE WHEN :vehicleModel IS NOT NULL AND LOWER(pl.vehicle_model) = LOWER(:vehicleModel) THEN 1 ELSE 0 END
                           + CASE WHEN :vehicleYear IS NOT NULL AND pl.vehicle_year = :vehicleYear THEN 1 ELSE 0 END) DESC,
                          pl.created_at DESC, pl.public_id DESC
                        LIMIT :limit
                        """)
                .bind("limit", limit);
        query = bindNullable(query, "category", request.category() == null ? null : request.category().name(), String.class);
        query = bindNullable(query, "condition", request.condition() == null ? null : request.condition().name(), String.class);
        query = bindNullable(query, "vehicleMake", request.vehicleMake(), String.class);
        query = bindNullable(query, "vehicleModel", request.vehicleModel(), String.class);
        query = bindNullable(query, "vehicleYear", request.vehicleYear(), Integer.class);
        return query.map((row, metadata) -> map(row)).all();
    }

    @Override
    public Flux<PartListingRecord> findBySellerPublicId(UUID sellerUserPublicId, int page, int size) {
        requirePublicId(sellerUserPublicId, "sellerUserPublicId");
        requirePage(page, size);
        return databaseClient.sql(SELECT + """
                        WHERE au.public_id = :sellerPublicId
                        ORDER BY pl.created_at DESC, pl.id DESC
                        LIMIT :size OFFSET :offset
                        """)
                .bind("sellerPublicId", sellerUserPublicId)
                .bind("size", size)
                .bind("offset", (long) page * size)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<Long> countBySellerPublicId(UUID sellerUserPublicId) {
        requirePublicId(sellerUserPublicId, "sellerUserPublicId");
        return databaseClient.sql("""
                        SELECT count(*) AS total
                        FROM part_listings pl
                        JOIN app_users au ON au.id = pl.seller_user_id
                        WHERE au.public_id = :sellerPublicId
                        """)
                .bind("sellerPublicId", sellerUserPublicId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one();
    }

    @Override
    public Mono<PartListingRecord> updateStatus(UUID publicId, UUID sellerUserPublicId, String status,
                                                long expectedVersion, Instant updatedAt) {
        requirePublicId(publicId, "publicId");
        requirePublicId(sellerUserPublicId, "sellerUserPublicId");
        requireText(status, "status");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must be >= 0");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt is required");
        return transactions.transactional(databaseClient.sql("""
                        UPDATE part_listings pl
                        SET status = :status, business_status = :status,
                            version = pl.version + 1, updated_at = :updatedAt
                        FROM app_users au
                        WHERE pl.public_id = :publicId
                          AND au.id = pl.seller_user_id
                          AND au.public_id = :sellerPublicId
                          AND pl.version = :expectedVersion
                        RETURNING pl.public_id
                        """)
                .bind("status", status)
                .bind("updatedAt", updatedAt)
                .bind("publicId", publicId)
                .bind("sellerPublicId", sellerUserPublicId)
                .bind("expectedVersion", expectedVersion)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one())
                .flatMap(this::findByPublicId);
    }

    private static PartListingRecord map(io.r2dbc.spi.Row row) {
        return new PartListingRecord(
                row.get("public_id", UUID.class),
                row.get("seller_public_id", UUID.class),
                row.get("name", String.class),
                row.get("description", String.class),
                row.get("vehicle_make", String.class),
                row.get("vehicle_model", String.class),
                row.get("vehicle_year", Integer.class),
                enumValue(row.get("condition", String.class), Condition.class),
                enumValue(row.get("category", String.class), Category.class),
                row.get("amount_minor", Long.class),
                row.get("currency", String.class),
                row.get("status", String.class),
                row.get("business_status", String.class),
                row.get("delivery_status", String.class),
                row.get("storage_status", String.class),
                row.get("view_count", Long.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class),
                row.get("version", Long.class));
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                       String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                  String name, String value) {
        return bindNullable(spec, name, value, String.class);
    }

    private static void requirePublicId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireCurrency(String value) {
        requireText(value, "currency");
        try {
            Currency.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currency is not supported", exception);
        }
    }

    private static void requirePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
}
