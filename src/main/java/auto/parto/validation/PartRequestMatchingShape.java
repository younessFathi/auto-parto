package auto.parto.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Defines the finite filter groups supported by the indexed marketplace matcher.
 */
public final class PartRequestMatchingShape {

    public static final String ERROR_MESSAGE =
            "unsupported matching filters; use condition, category with condition, "
                    + "a complete vehicle tuple, or category and condition with a complete vehicle tuple";

    private PartRequestMatchingShape() {
    }

    public static boolean isSupported(String condition, String category,
                                      String vehicleMake, String vehicleModel, Integer vehicleYear) {
        boolean hasVehicleMake = vehicleMake != null;
        boolean hasVehicleModel = vehicleModel != null;
        boolean hasVehicleYear = vehicleYear != null;
        boolean hasAnyVehicle = hasVehicleMake || hasVehicleModel || hasVehicleYear;
        boolean completeVehicle = hasVehicleMake && hasVehicleModel && hasVehicleYear;

        if (hasAnyVehicle && !completeVehicle) {
            return false;
        }
        if (!hasAnyVehicle) {
            return category == null || condition != null;
        }
        return (category == null && condition == null)
                || (category != null && condition != null);
    }

    public static void requireSupported(String condition, String category,
                                        String vehicleMake, String vehicleModel, Integer vehicleYear) {
        if (!isSupported(condition, category, vehicleMake, vehicleModel, vehicleYear)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_MESSAGE);
        }
    }
}
