package auto.parto.security;

import auto.parto.api.BuyerController;
import auto.parto.api.BuyerProfileController;
import auto.parto.api.ImageController;
import auto.parto.api.NotificationController;
import auto.parto.api.OfferController;
import auto.parto.api.PartListingController;
import auto.parto.api.PartRequestController;
import auto.parto.api.PieceController;
import auto.parto.api.SellerController;
import auto.parto.api.SellerProfileController;
import auto.parto.api.VersionedNotificationController;
import auto.parto.service.PieceService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiBoundaryRouteContractTest {

    @Test
    void domainControllersExposeVersionedCanonicalRoutes() {
        assertThat(paths(PartRequestController.class)).contains("/api/v1/part-requests");
        assertThat(paths(PartListingController.class)).contains("/api/v1/part-listings");
        assertThat(paths(OfferController.class)).contains("/api/v1/offers");
        assertThat(paths(ImageController.class)).containsExactly("/api/v1/files");
        assertThat(paths(VersionedNotificationController.class)).contains("/api/v1/notifications");
        assertThat(paths(BuyerProfileController.class)).contains("/api/v1/profiles/buyer");
        assertThat(paths(SellerProfileController.class)).contains("/api/v1/profiles/seller");
        assertThat(paths(BuyerController.class)).contains("/api/v1/buyers");
        assertThat(paths(SellerController.class)).contains("/api/v1/sellers");
    }

    @Test
    void pieceServiceHasNoSuccessfulNoOpUpdateMutation() {
        assertThat(Arrays.stream(PieceService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("update");
    }

    @Test
    void everyRetainedLegacySurfaceHasAnExplicitFutureSunset() {
        assertThat(paths(PieceController.class)).contains("/pieces");
        assertThat(methodPaths(NotificationController.class, GetMapping.class))
                .contains("/notifications/subscribe", "/notifications/subscribe/{userId}")
                .doesNotContain("/api/v1/notifications/subscribe", "/api/v1/notifications/subscribe/{userId}");
        assertThat(Arrays.stream(NotificationController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("subscribe") || method.getName().equals("subscribeToUser"))
                .allMatch(method -> method.isAnnotationPresent(Deprecated.class)))
                .isTrue();
        assertThat(methodPaths(VersionedNotificationController.class, GetMapping.class))
                .containsExactly("/stream");
        assertThat(methodPaths(NotificationController.class, PostMapping.class))
                .contains("/api/v1/notifications/broadcast", "/api/v1/notifications/send");
        assertThat(java.time.LocalDate.of(2027, 4, 1))
                .isAfter(java.time.LocalDate.now());
    }

    private static List<String> methodPaths(Class<?> controller, Class<? extends java.lang.annotation.Annotation> mappingType) {
        return Arrays.stream(controller.getDeclaredMethods())
                .flatMap(method -> {
                    if (mappingType == GetMapping.class) {
                        GetMapping mapping = method.getAnnotation(GetMapping.class);
                        return mapping == null ? java.util.stream.Stream.empty()
                                : Arrays.stream(mapping.value());
                    }
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping == null ? java.util.stream.Stream.empty()
                            : Arrays.stream(mapping.value());
                })
                .toList();
    }

    private static List<String> paths(Class<?> controller) {
        return List.of(controller.getAnnotation(RequestMapping.class).value());
    }
}
