package auto.parto.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.buyer-identity.enabled:false}' == 'true'")
public final class R2dbcBuyerProfileRepository implements BuyerProfileRepository {

    private static final String PROFILE_COLUMNS = """
            bp.public_id, au.public_id AS user_public_id, bp.first_name, bp.last_name,
            bp.phone_number, bp.picture_url
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2dbcBuyerProfileRepository(DatabaseClient databaseClient,
                                       TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<BuyerProfileRecord> findByUserPublicId(UUID userPublicId) {
        requireUserPublicId(userPublicId);
        return databaseClient.sql(selectProfileSql())
                .bind("userPublicId", userPublicId)
                .map((row, metadata) -> mapProfile(row))
                .one();
    }

    @Override
    public Mono<BuyerProfileRecord> updateByUserPublicId(UUID userPublicId, String firstName, String lastName,
                                                         String phoneNumber, String pictureUrl) {
        requireUserPublicId(userPublicId);
        DatabaseClient.GenericExecuteSpec update = databaseClient.sql("""
                        UPDATE buyer_profiles bp
                        SET first_name = :firstName,
                            last_name = :lastName,
                            phone_number = :phoneNumber,
                            picture_url = :pictureUrl
                        FROM app_users au
                        WHERE au.id = bp.user_id
                          AND au.public_id = :userPublicId
                          AND au.role = 'BUYER'
                        RETURNING bp.public_id
                        """)
                .bind("userPublicId", userPublicId);
        update = bindNullable(update, "firstName", firstName);
        update = bindNullable(update, "lastName", lastName);
        update = bindNullable(update, "phoneNumber", phoneNumber);
        update = bindNullable(update, "pictureUrl", pictureUrl);

        return transactionalOperator.transactional(update.fetch().rowsUpdated())
                .flatMap(rows -> rows == 1 ? findByUserPublicId(userPublicId) : Mono.empty());
    }

    private String selectProfileSql() {
        return """
                SELECT %s
                FROM buyer_profiles bp
                JOIN app_users au ON au.id = bp.user_id
                WHERE au.public_id = :userPublicId
                  AND au.role = 'BUYER'
                """.formatted(PROFILE_COLUMNS);
    }

    private static BuyerProfileRecord mapProfile(io.r2dbc.spi.Row row) {
        return new BuyerProfileRecord(
                row.get("public_id", UUID.class),
                row.get("user_public_id", UUID.class),
                row.get("first_name", String.class),
                row.get("last_name", String.class),
                row.get("phone_number", String.class),
                row.get("picture_url", String.class));
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static void requireUserPublicId(UUID userPublicId) {
        if (userPublicId == null) {
            throw new IllegalArgumentException("userPublicId is required");
        }
    }
}
