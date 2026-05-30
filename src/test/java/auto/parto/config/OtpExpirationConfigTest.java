package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OtpExpirationConfigTest {

    @Test
    void otpExpirationMinutesIsConfiguredInApplicationYml() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application.yml", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();

        Object minutes = sources.get(0).getProperty("otp.expiration.minutes");
        assertThat(minutes).isEqualTo(5);
    }

    @Test
    void oldOptPropertyIsNotPresent() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application.yml", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();

        Object oldProp = sources.get(0).getProperty("opt.expiration.minutes");
        assertThat(oldProp).isNull();
    }
}
