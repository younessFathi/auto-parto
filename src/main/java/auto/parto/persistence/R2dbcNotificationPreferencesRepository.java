package auto.parto.persistence;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.notification-preferences", name = "enabled",
        havingValue = "true")
public final class R2dbcNotificationPreferencesRepository implements NotificationPreferencesRepository {

    private static final String SELECT = """
            SELECT preferences.public_id, owner.public_id AS user_public_id,
                   preferences.email_enabled, preferences.push_enabled, preferences.sms_enabled,
                   preferences.new_offer_enabled, preferences.price_alert_enabled,
                   preferences.search_match_enabled, preferences.order_update_enabled,
                   preferences.marketing_enabled, preferences.created_at, preferences.updated_at
            FROM notification_preferences preferences
            JOIN app_users owner ON owner.id = preferences.user_id
            """;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcNotificationPreferencesRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<NotificationPreferencesRecord> findByUserPublicId(UUID userPublicId) {
        requireId(userPublicId);
        return databaseClient.sql(SELECT + " WHERE owner.public_id = :userPublicId")
                .bind("userPublicId", userPublicId).map((row, metadata) -> map(row)).one();
    }

    @Override
    public Mono<NotificationPreferencesRecord> save(UUID userPublicId, NotificationPreferencesRecord preferences) {
        requireId(userPublicId);
        if (preferences == null) throw new IllegalArgumentException("preferences are required");
        var statement = databaseClient.sql("""
                        INSERT INTO notification_preferences
                            (public_id, user_id, email_enabled, push_enabled, sms_enabled, new_offer_enabled,
                             price_alert_enabled, search_match_enabled, order_update_enabled, marketing_enabled)
                        SELECT COALESCE(:publicId, gen_random_uuid()), owner.id, :emailEnabled, :pushEnabled,
                               :smsEnabled, :newOfferEnabled, :priceAlertEnabled, :searchMatchEnabled,
                               :orderUpdateEnabled, :marketingEnabled
                        FROM app_users owner WHERE owner.public_id = :userPublicId AND owner.status = 'ACTIVE'
                        ON CONFLICT (user_id) DO UPDATE SET email_enabled = EXCLUDED.email_enabled,
                            push_enabled = EXCLUDED.push_enabled, sms_enabled = EXCLUDED.sms_enabled,
                            new_offer_enabled = EXCLUDED.new_offer_enabled,
                            price_alert_enabled = EXCLUDED.price_alert_enabled,
                            search_match_enabled = EXCLUDED.search_match_enabled,
                            order_update_enabled = EXCLUDED.order_update_enabled,
                            marketing_enabled = EXCLUDED.marketing_enabled,
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING public_id
                        """)
                .bind("userPublicId", userPublicId)
                .bind("emailEnabled", preferences.emailEnabled()).bind("pushEnabled", preferences.pushEnabled())
                .bind("smsEnabled", preferences.smsEnabled()).bind("newOfferEnabled", preferences.newOfferEnabled())
                .bind("priceAlertEnabled", preferences.priceAlertEnabled())
                .bind("searchMatchEnabled", preferences.searchMatchEnabled())
                .bind("orderUpdateEnabled", preferences.orderUpdateEnabled())
                .bind("marketingEnabled", preferences.marketingEnabled());
        statement = preferences.publicId() == null ? statement.bindNull("publicId", UUID.class)
                : statement.bind("publicId", preferences.publicId());
        return transactions.transactional(statement
                .map((row, metadata) -> row.get("public_id", UUID.class)).one()
                .flatMap(this::findByPublicId));
    }

    private Mono<NotificationPreferencesRecord> findByPublicId(UUID publicId) {
        return databaseClient.sql(SELECT + " WHERE preferences.public_id = :publicId")
                .bind("publicId", publicId).map((row, metadata) -> map(row)).one();
    }

    private static NotificationPreferencesRecord map(Row row) {
        return new NotificationPreferencesRecord(row.get("public_id", UUID.class), row.get("user_public_id", UUID.class),
                row.get("email_enabled", Boolean.class), row.get("push_enabled", Boolean.class),
                row.get("sms_enabled", Boolean.class), row.get("new_offer_enabled", Boolean.class),
                row.get("price_alert_enabled", Boolean.class), row.get("search_match_enabled", Boolean.class),
                row.get("order_update_enabled", Boolean.class), row.get("marketing_enabled", Boolean.class),
                row.get("created_at", Instant.class), row.get("updated_at", Instant.class));
    }

    private static void requireId(UUID id) { if (id == null) throw new IllegalArgumentException("userPublicId is required"); }
}
