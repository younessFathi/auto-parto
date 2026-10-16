package auto.parto.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import auto.parto.validation.PartRequestMatchingShape;

public record PartRequestCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 4000) String description,
        @Size(max = 32) String condition,
        @Size(max = 64) String category,
        @Size(max = 120) String vehicleMake,
        @Size(max = 120) String vehicleModel,
        @Min(1886) @Max(2100) Integer vehicleYear) {

    @AssertTrue(message = PartRequestMatchingShape.ERROR_MESSAGE)
    public boolean hasSupportedMatchingShape() {
        return PartRequestMatchingShape.isSupported(condition, category, vehicleMake, vehicleModel, vehicleYear);
    }
}
