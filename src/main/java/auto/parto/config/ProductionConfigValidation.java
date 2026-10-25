package auto.parto.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
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
        if (!"kubernetes".equalsIgnoreCase(authMethod) && !"jwt".equalsIgnoreCase(authMethod)) {
            errors.add("auto.parto.secrets.openbao.auth-method=kubernetes|jwt");
        }
    }

    private void validatePostgresql(List<String> errors) {
        String url = value("spring.r2dbc.url");
        validateRequiredProperty("spring.r2dbc.url", errors);
        if (url != null && (!url.startsWith("r2dbc:postgresql://") || url.contains("localhost"))) errors.add("spring.r2dbc.url must be a protected PostgreSQL URL");
        validateRequiredProperty("spring.r2dbc.username", errors);
        validateRequiredProperty("spring.r2dbc.password", errors);
    }

    private void validateRedis(List<String> errors) {
        validateRequiredProperty("spring.data.redis.host", errors);
        validatePort("spring.data.redis.port", errors);
        validateRequiredProperty("spring.data.redis.password", errors);
        if ("localhost".equalsIgnoreCase(value("spring.data.redis.host"))) errors.add("spring.data.redis.host must not be localhost");
    }

    private void validateJwt(List<String> errors) {
        String secret = value("jwt.secretKey");
        validateRequiredProperty("jwt.secretKey", errors);
        if (secret != null) {
            try {
                if (Base64.getDecoder().decode(secret).length < 32) errors.add("jwt.secretKey must decode to at least 256 bits");
            } catch (IllegalArgumentException exception) {
                errors.add("jwt.secretKey must be Base64 encoded");
            }
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

    private void validatePort(String propertyName, List<String> errors) {
        try {
            int port = Integer.parseInt(value(propertyName));
            if (port < 1 || port > 65535) errors.add(propertyName + " must be between 1 and 65535");
        } catch (NumberFormatException exception) { errors.add(propertyName + " must be a valid port"); }
    }

    private void validateHttpsUri(String propertyName, List<String> errors) {
        String propertyValue = value(propertyName);
        try {
            if (propertyValue == null || !"https".equalsIgnoreCase(URI.create(propertyValue).getScheme())) errors.add(propertyName + " must use HTTPS");
        } catch (IllegalArgumentException exception) { errors.add(propertyName + " must be a valid HTTPS URI"); }
    }

    private boolean isBlank(String propertyName) {
        String propertyValue = value(propertyName);
        return propertyValue == null || propertyValue.isBlank() || propertyValue.contains("${");
    }

    private String value(String propertyName) { return environment.getProperty(propertyName); }
}
