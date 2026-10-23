package auto.parto.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductionConfigValidation implements InitializingBean {

    private static final String PROD_PROFILE = "prod";

    private final Environment environment;

    public ProductionConfigValidation(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isProdProfileActive()) {
            return;
        }

        List<String> missingProperties = new ArrayList<>();
        validateRequiredProperty("jwt.secretKey", missingProperties);
        validateRequiredProperty("resend.api-key", missingProperties);
        validateRequiredProperty("spring.data.redis.password", missingProperties);

        if (!missingProperties.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required production configuration properties: " + String.join(", ", missingProperties)
            );
        }
    }

    private boolean isProdProfileActive() {
        for (String profile : environment.getActiveProfiles()) {
            if (PROD_PROFILE.equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private void validateRequiredProperty(String propertyName, List<String> missingProperties) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            missingProperties.add(propertyName);
        }
    }
}
