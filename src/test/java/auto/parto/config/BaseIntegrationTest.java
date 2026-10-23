package auto.parto.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for all integration tests.
 * Spins up a real Redis container. PostgreSQL repository integration tests own
 * their database container and application-level tests mock persistence where
 * they do not exercise a database contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "RESEND_API_KEY=dummy",
    "CLIENT_ID=dummy",
    "CLIENT_SECRET=dummy",
    "JWT_SECRET=bmgTK4gXGmWEbq3jc3zluPcMtomqqpMSO1VY7R0QqYQ=",
    "SID=dummy",
    "AUTH=dummy",
    "REDIS_PASSWORD=",
    "firebase.enabled=false",
    "auto.parto.persistence.postgresql.enabled=true",
    "auto.parto.persistence.postgresql.identity.enabled=true",
    "auto.parto.persistence.postgresql.buyer-identity.enabled=true",
    "auto.parto.persistence.postgresql.marketplace.enabled=true",
    "auto.parto.persistence.postgresql.wallet.enabled=true",
    "auto.parto.persistence.postgresql.device-tokens.enabled=true",
    "auto.parto.persistence.postgresql.notifications.enabled=true",
    "auto.parto.persistence.postgresql.files.enabled=true",
    "auto.parto.persistence.postgresql.notification-preferences.enabled=true",
    "security.otp.pepper=test-only-otp-pepper",
    "auto.parto.persistence.postgresql.cutover.enabled=false",
    "management.health.r2dbc.enabled=false",
    "management.health.db.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }
}
