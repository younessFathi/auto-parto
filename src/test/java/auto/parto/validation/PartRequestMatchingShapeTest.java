package auto.parto.validation;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartRequestMatchingShapeTest {

    @Test
    void acceptsTheFiveIndexedFilterGroups() {
        assertThat(PartRequestMatchingShape.isSupported(null, null, null, null, null)).isTrue();
        assertThat(PartRequestMatchingShape.isSupported(Condition.NEW.name(), null, null, null, null)).isTrue();
        assertThat(PartRequestMatchingShape.isSupported(Condition.NEW.name(), Category.CAR.name(), null, null, null))
                .isTrue();
        assertThat(PartRequestMatchingShape.isSupported(null, null, "Toyota", "Corolla", 2020)).isTrue();
        assertThat(PartRequestMatchingShape.isSupported(Condition.NEW.name(), Category.CAR.name(),
                "Toyota", "Corolla", 2020)).isTrue();
    }

    @Test
    void rejectsCategoryOnlyPartialVehicleAndMixedVehicleGroups() {
        assertThat(PartRequestMatchingShape.isSupported(null, Category.CAR.name(), null, null, null)).isFalse();
        assertThat(PartRequestMatchingShape.isSupported(null, null, "Toyota", null, 2020)).isFalse();
        assertThat(PartRequestMatchingShape.isSupported(null, null, null, "Corolla", null)).isFalse();
        assertThat(PartRequestMatchingShape.isSupported(Condition.NEW.name(), null,
                "Toyota", "Corolla", 2020)).isFalse();
        assertThat(PartRequestMatchingShape.isSupported(null, Category.CAR.name(),
                "Toyota", "Corolla", 2020)).isFalse();
    }

    @Test
    void reportsUnsupportedShapeAsBadRequest() {
        assertThatThrownBy(() -> PartRequestMatchingShape.requireSupported(
                null, Category.CAR.name(), null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
