package auto.parto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecretsManagerServiceTest {

    @InjectMocks
    private SecretsManagerService secretsManagerService;

    private static final String BASE_SECRET = "H1lq8Mx5WjD3+KPxBlj+F+Qo5rFkNc1uWy2FpiGpWXM=";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(secretsManagerService, "baseSecretKey", BASE_SECRET);
    }

    @Test
    void getKeyForVersion_returnsBaseSecret_whenVersionIsNull() {
        String key = secretsManagerService.getKeyForVersion(null);

        assertThat(key).isEqualTo(BASE_SECRET);
    }

    @Test
    void getKeyForVersion_returnsBaseSecret_whenVersionIsBlank() {
        String key = secretsManagerService.getKeyForVersion("");

        assertThat(key).isEqualTo(BASE_SECRET);
    }

    @Test
    void getKeyForVersion_derivesKeyForVersion1() {
        String key = secretsManagerService.getKeyForVersion("1");

        assertThat(key).isNotEmpty();
        assertThat(key).isNotEqualTo(BASE_SECRET);
    }

    @Test
    void getKeyForVersion_derivesKeyForVersion2() {
        String key1 = secretsManagerService.getKeyForVersion("1");
        String key2 = secretsManagerService.getKeyForVersion("2");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void getKeyForVersion_cachesDerivedKey() {
        String key1 = secretsManagerService.getKeyForVersion("5");
        String key2 = secretsManagerService.getKeyForVersion("5");

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void rotateKey_incrementsVersion() {
        String newVersion = secretsManagerService.rotateKey(null);

        assertThat(newVersion).isEqualTo("1");
    }

    @Test
    void rotateKey_incrementsFromCurrentVersion() {
        String newVersion = secretsManagerService.rotateKey("3");

        assertThat(newVersion).isEqualTo("4");
    }

    @Test
    void getKeyForVersion_differentVersionsProduceDifferentKeys() {
        String key1 = secretsManagerService.getKeyForVersion("10");
        String key2 = secretsManagerService.getKeyForVersion("20");
        String key3 = secretsManagerService.getKeyForVersion("100");

        assertThat(key1).isNotEqualTo(key2);
        assertThat(key2).isNotEqualTo(key3);
        assertThat(key1).isNotEqualTo(key3);
    }

    @Test
    void getKeyForVersion_producesValidBase64Key() {
        String key = secretsManagerService.getKeyForVersion("1");

        byte[] decoded = java.util.Base64.getDecoder().decode(key);
        assertThat(decoded).hasSize(64);
    }
}