package auto.parto.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class ProductionConfigValidation implements InitializingBean {
    private static final String PROD_PROFILE = "prod";
    private final Environment environment;

    public ProductionConfigValidation(Environment environment) { this.environment = environment; }

    @Override
    public void afterPropertiesSet() {
        if (!isProdProfileActive()) return;
        List<String> errors = new ArrayList<>();
        validateSecretDelivery(errors);
        validatePostgresql(errors);
        validatePostgresqlCutover(errors);
        validateRedis(errors);
        validateJwt(errors);
        validateStorage(errors);
        validateConditionalProviders(errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid required production configuration: " + String.join(", ", errors));
        }
    }

    private void validateSecretDelivery(List<String> errors) {
        if (!"openbao".equalsIgnoreCase(value("auto.parto.secrets.provider"))) errors.add("auto.parto.secrets.provider=openbao");
        validateHttpsUri("auto.parto.secrets.openbao.address", errors);
        validateRequiredProperty("auto.parto.secrets.openbao.role", errors);
        String authMethod = value("auto.parto.secrets.openbao.auth-method");
        if (!"kubernetes".equalsIgnoreCase(authMethod) && !"jwt".equalsIgnoreCase(authMethod)
                && !"approle-agent".equalsIgnoreCase(authMethod)) {
            errors.add("auto.parto.secrets.openbao.auth-method=kubernetes|jwt|approle-agent");
        }
    }

    private void validatePostgresql(List<String> errors) {
        String url = value("spring.r2dbc.url");
        validateRequiredProperty("spring.r2dbc.url", errors);
        if (url != null && !url.startsWith("r2dbc:postgresql://")) {
            errors.add("spring.r2dbc.url must be a protected PostgreSQL URL");
        } else if (url != null && hasEmbeddedR2dbcCredentials(url)) {
            errors.add("spring.r2dbc.url must not contain embedded credentials");
        } else if (url != null && isLoopbackR2dbcUrl(url)) {
            errors.add("spring.r2dbc.url must not use a loopback host");
        }
        validateRequiredProperty("spring.r2dbc.username", errors);
        validateRequiredProperty("spring.r2dbc.password", errors);
    }

    private void validateRedis(List<String> errors) {
        validateRequiredProperty("spring.data.redis.host", errors);
        validatePort("spring.data.redis.port", errors);
        validateRequiredProperty("spring.data.redis.password", errors);
        if (isLoopbackHost(value("spring.data.redis.host"))) errors.add("spring.data.redis.host must not be a loopback host");
    }

    private void validatePostgresqlCutover(List<String> errors) {
        requireTrue("auto.parto.persistence.postgresql.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.identity.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.buyer-identity.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.marketplace.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.wallet.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.device-tokens.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.notifications.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.files.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.notification-preferences.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.cutover.enabled", errors);
        requireTrue("auto.parto.persistence.postgresql.cutover.api-contracts-ready", errors);
        requireTrue("auto.parto.persistence.postgresql.cutover.mongo-disabled", errors);
        requireTrue("auto.parto.persistence.postgresql.cutover.storage-reconciliation.enabled", errors);
        if (!isMongoAutoConfigurationExcluded()) {
            errors.add("spring.autoconfigure.exclude must disable Mongo reactive runtime");
        }
    }

    private boolean isMongoAutoConfigurationExcluded() {
        String direct = value("spring.autoconfigure.exclude");
        StringBuilder exclusions = new StringBuilder(direct == null ? "" : direct);
        for (int index = 0; index < 8; index++) {
            String indexed = value("spring.autoconfigure.exclude[" + index + "]");
            if (indexed != null) exclusions.append(',').append(indexed);
        }
        String configured = exclusions.toString();
        return configured.contains("MongoReactiveAutoConfiguration")
                && configured.contains("MongoReactiveRepositoriesAutoConfiguration");
    }

    private void validateJwt(List<String> errors) {
        if (!"RS256".equalsIgnoreCase(value("jwt.signing.algorithm"))) errors.add("jwt.signing.algorithm=RS256");
        validateRequiredProperty("jwt.signing.key-id", errors);
        validateRequiredProperty("jwt.signing.private-key", errors);
        String publicKeys = value("jwt.signing.verification-keys");
        if (isBlank("jwt.signing.verification-keys") || publicKeys == null || !publicKeys.contains("\"" + value("jwt.signing.key-id") + "\"")) {
            errors.add("jwt.signing.verification-keys must contain the active key id");
        }
    }

    private void validateStorage(List<String> errors) {
        if (!"s3".equalsIgnoreCase(value("auto.parto.storage.provider"))) errors.add("auto.parto.storage.provider=s3");
        validateHttpsUri("auto.parto.storage.s3.endpoint", errors);
        validateRequiredProperty("auto.parto.storage.s3.bucket", errors);
        validateRequiredProperty("auto.parto.storage.s3.access-key", errors);
        validateRequiredProperty("auto.parto.storage.s3.secret-key", errors);
    }

    private void validateConditionalProviders(List<String> errors) {
        validateBoolean("resend.enable", errors);
        if (Boolean.parseBoolean(value("resend.enable"))) validateRequiredProperty("resend.api-key", errors);
        validateBoolean("firebase.enabled", errors);
        if (Boolean.parseBoolean(value("firebase.enabled")) && isBlank("firebase.credentials.json") && isBlank("firebase.credentials.path")) errors.add("firebase.credentials.json or firebase.credentials.path");
        validateBoolean("auto.parto.audit.enabled", errors);
        if (Boolean.parseBoolean(value("auto.parto.audit.enabled"))) {
            validateRequiredProperty("auto.parto.audit.r2dbc-url", errors);
            validateRequiredProperty("auto.parto.audit.username", errors);
            validateRequiredProperty("auto.parto.audit.password", errors);
        }
    }

    private boolean isProdProfileActive() {
        for (String profile : environment.getActiveProfiles()) if (PROD_PROFILE.equals(profile)) return true;
        return false;
    }

    private void validateRequiredProperty(String propertyName, List<String> errors) { if (isBlank(propertyName)) errors.add(propertyName); }

    private void validateBoolean(String propertyName, List<String> errors) {
        String propertyValue = value(propertyName);
        if (!"true".equalsIgnoreCase(propertyValue) && !"false".equalsIgnoreCase(propertyValue)) errors.add(propertyName + " must be true or false");
    }

    private void requireTrue(String propertyName, List<String> errors) {
        if (!"true".equalsIgnoreCase(value(propertyName))) errors.add(propertyName + "=true");
    }

    private void validatePort(String propertyName, List<String> errors) {
        try {
            int port = Integer.parseInt(value(propertyName));
            if (port < 1 || port > 65535) errors.add(propertyName + " must be between 1 and 65535");
        } catch (NumberFormatException exception) { errors.add(propertyName + " must be a valid port"); }
    }

    private void validateHttpsUri(String propertyName, List<String> errors) {
        String propertyValue = value(propertyName);
        try {
            URI uri = propertyValue == null ? null : URI.create(propertyValue);
            if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                errors.add(propertyName + " must use HTTPS");
            } else if (isLoopbackHost(uri.getHost())) {
                errors.add(propertyName + " must not use a loopback host");
            }
        } catch (IllegalArgumentException exception) { errors.add(propertyName + " must be a valid HTTPS URI"); }
    }

    private boolean isLoopbackR2dbcUrl(String value) {
        try {
            String authority = value.substring("r2dbc:postgresql://".length());
            return isLoopbackHost(URI.create("http://" + authority).getHost());
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private boolean hasEmbeddedR2dbcCredentials(String value) {
        try {
            String authority = value.substring("r2dbc:postgresql://".length()).split("[/ ?]", 2)[0];
            return authority.contains("@") || value.matches("(?i).*([?&])(user|username|password)=.*");
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) return true;
        String normalized = host.trim().toLowerCase();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        if ("localhost".equals(normalized)) return true;
        if (!looksLikeIpLiteral(normalized)) return false;
        try {
            return InetAddress.getByName(normalized).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return true;
        }
    }

    private boolean looksLikeIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+") || host.startsWith("::ffff:");
    }

    private boolean isBlank(String propertyName) {
        String propertyValue = value(propertyName);
        return propertyValue == null || propertyValue.isBlank() || propertyValue.contains("${");
    }

    private String value(String propertyName) { return environment.getProperty(propertyName); }
}
