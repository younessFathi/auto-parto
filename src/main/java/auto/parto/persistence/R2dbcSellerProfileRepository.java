package auto.parto.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class R2dbcSellerProfileRepository implements SellerProfileRepository {

    private static final String PROFILE_COLUMNS = """
            sp.public_id, au.public_id AS user_public_id, sp.first_name, sp.last_name,
            sp.phone_number, sp.part_type, sp.description
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2dbcSellerProfileRepository(DatabaseClient databaseClient,
                                        TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<SellerProfileRecord> findByEmail(String email) {
        requireEmail(email);
        return databaseClient.sql(selectProfileSql())
                .bind("email", email)
                .map((row, metadata) -> mapProfile(row))
                .one();
    }

    @Override
    public Mono<SellerProfileRecord> findByUserPublicId(java.util.UUID userPublicId) {
        requireUserPublicId(userPublicId);
        return databaseClient.sql(selectProfileByUserPublicIdSql())
                .bind("userPublicId", userPublicId)
                .map((row, metadata) -> mapProfile(row))
                .one();
    }

    @Override
    public Mono<SellerProfileRecord> updateByUserPublicId(java.util.UUID userPublicId,
                                                          String firstName, String lastName,
                                                          String phoneNumber, String partType,
                                                          String description) {
        requireUserPublicId(userPublicId);
        DatabaseClient.GenericExecuteSpec update = databaseClient.sql("""
                        UPDATE seller_profiles sp
                        SET first_name = :firstName,
                            last_name = :lastName,
                            phone_number = :phoneNumber,
                            part_type = :partType,
                            description = :description
                        FROM app_users au
                        WHERE au.id = sp.user_id
                          AND au.public_id = :userPublicId
                          AND au.role = 'SELLER'
                        RETURNING sp.public_id
                        """)
                .bind("userPublicId", userPublicId);
        update = bindNullable(update, "firstName", firstName);
        update = bindNullable(update, "lastName", lastName);
        update = bindNullable(update, "phoneNumber", phoneNumber);
        update = bindNullable(update, "partType", partType);
        update = bindNullable(update, "description", description);

        return transactionalOperator.transactional(update.fetch().rowsUpdated())
                .flatMap(rows -> rows == 1
                        ? findByUserPublicId(userPublicId)
                        : Mono.empty());
    }

    private String selectProfileSql() {
        return """
                SELECT %s
                FROM seller_profiles sp
                JOIN app_users au ON au.id = sp.user_id
                WHERE LOWER(au.email) = LOWER(:email)
                  AND au.role = 'SELLER'
                """.formatted(PROFILE_COLUMNS);
    }

    private String selectProfileByUserPublicIdSql() {
        return """
                SELECT %s
                FROM seller_profiles sp
                JOIN app_users au ON au.id = sp.user_id
                WHERE au.public_id = :userPublicId
                  AND au.role = 'SELLER'
                """.formatted(PROFILE_COLUMNS);
    }

    private static SellerProfileRecord mapProfile(io.r2dbc.spi.Row row) {
        return new SellerProfileRecord(
                row.get("public_id", java.util.UUID.class),
                row.get("user_public_id", java.util.UUID.class),
                row.get("first_name", String.class),
                row.get("last_name", String.class),
                row.get("phone_number", String.class),
                row.get("part_type", String.class),
                row.get("description", String.class));
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static void requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
    }

    private static void requireUserPublicId(java.util.UUID userPublicId) {
        if (userPublicId == null) {
            throw new IllegalArgumentException("userPublicId is required");
        }
    }
}
