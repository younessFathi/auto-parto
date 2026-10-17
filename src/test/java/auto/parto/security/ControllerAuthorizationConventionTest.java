package auto.parto.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerAuthorizationConventionTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            auto.parto.api.BuyerController.class,
            auto.parto.api.BuyerProfileController.class,
            auto.parto.api.FcmNotificationController.class,
            auto.parto.api.ImageController.class,
            auto.parto.api.MarketplaceMatchingController.class,
            auto.parto.api.NotificationController.class,
            auto.parto.api.NotificationPreferencesController.class,
            auto.parto.api.OfferController.class,
            auto.parto.api.PartListingController.class,
            auto.parto.api.PartRequestController.class,
            auto.parto.api.PieceController.class,
            auto.parto.api.SellerController.class,
            auto.parto.api.SellerProfileController.class
    );

    @Test
    void protectedControllerMethodsDeclareMethodSecurityRule() {
        CONTROLLERS.forEach(controller ->
                java.util.Arrays.stream(controller.getDeclaredMethods())
                        .filter(ControllerAuthorizationConventionTest::isMappedEndpoint)
                        .forEach(method -> assertThat(method.isAnnotationPresent(PreAuthorize.class))
                                .as("%s.%s", controller.getSimpleName(), method.getName())
                                .isTrue()));
    }

    private static boolean isMappedEndpoint(java.lang.reflect.Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}
