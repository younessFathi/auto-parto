package auto.parto.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresqlCutoverConfigurationTest {

    @Test
    void acceptsCutoverOnlyWhenEveryBoundaryIsEnabled() {
        assertThatCode(() -> new PostgresqlCutoverConfiguration(true, true, true, true,
                true, true, true, true, true, true, true, true)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPartiallyEnabledCutover() {
        assertThatThrownBy(() -> new PostgresqlCutoverConfiguration(true, true, true, true,
                true, true, true, false, true, true, true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("every persistence boundary");
    }
}
