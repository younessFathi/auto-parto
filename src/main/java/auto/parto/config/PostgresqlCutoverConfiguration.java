package auto.parto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Fails startup when an explicit PostgreSQL cutover is only partially configured. */
@Configuration
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover", name = "enabled",
        havingValue = "true")
public final class PostgresqlCutoverConfiguration {

    public PostgresqlCutoverConfiguration(
            @Value("${auto.parto.persistence.postgresql.enabled:false}") boolean postgresqlEnabled,
            @Value("${auto.parto.persistence.postgresql.identity.enabled:false}") boolean sellerIdentityEnabled,
            @Value("${auto.parto.persistence.postgresql.buyer-identity.enabled:false}") boolean buyerIdentityEnabled,
            @Value("${auto.parto.persistence.postgresql.marketplace.enabled:false}") boolean marketplaceEnabled,
            @Value("${auto.parto.persistence.postgresql.wallet.enabled:false}") boolean walletEnabled,
            @Value("${auto.parto.persistence.postgresql.device-tokens.enabled:false}") boolean deviceTokensEnabled,
            @Value("${auto.parto.persistence.postgresql.notifications.enabled:false}") boolean notificationsEnabled,
            @Value("${auto.parto.persistence.postgresql.files.enabled:false}") boolean filesEnabled,
            @Value("${auto.parto.persistence.postgresql.notification-preferences.enabled:false}") boolean preferencesEnabled,
            @Value("${auto.parto.persistence.postgresql.cutover.api-contracts-ready:false}") boolean apiContractsReady,
            @Value("${auto.parto.persistence.postgresql.cutover.mongo-disabled:false}") boolean mongoDisabled,
            @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.enabled:false}") boolean storageReconciliationEnabled) {
        if (!postgresqlEnabled || !sellerIdentityEnabled || !buyerIdentityEnabled || !marketplaceEnabled
                || !walletEnabled || !deviceTokensEnabled || !notificationsEnabled || !filesEnabled
                || !preferencesEnabled || !apiContractsReady || !mongoDisabled || !storageReconciliationEnabled) {
            throw new IllegalStateException("PostgreSQL cutover requires every persistence boundary, API contract, and MongoDB shutdown gate");
        }
    }
}
