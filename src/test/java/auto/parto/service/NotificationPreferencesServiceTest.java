package auto.parto.service;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.model.NotificationPreferences;
import auto.parto.repository.NotificationPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferencesServiceTest {

    @Mock
    private NotificationPreferencesRepository repository;

    private NotificationPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferencesService(repository);
    }

    @Test
    void getPreferences_returnsDefault_whenNotFound() {
        when(repository.findByUserId("user-123")).thenReturn(Mono.empty());

        StepVerifier.create(service.getPreferences("user-123"))
                .expectNextMatches(dto ->
                    dto.getUserId().equals("user-123") &&
                    dto.isPushEnabled() &&
                    dto.isEmailEnabled() &&
                    !dto.isSmsEnabled()
                )
                .verifyComplete();
    }

    @Test
    void getPreferences_returnsStoredPreferences() {
        NotificationPreferences prefs = NotificationPreferences.builder()
                .userId("user-123")
                .emailEnabled(false)
                .pushEnabled(true)
                .smsEnabled(false)
                .newOfferEnabled(true)
                .build();

        when(repository.findByUserId("user-123")).thenReturn(Mono.just(prefs));

        StepVerifier.create(service.getPreferences("user-123"))
                .expectNextMatches(dto ->
                    !dto.isEmailEnabled() &&
                    dto.isPushEnabled() &&
                    dto.isNewOfferEnabled()
                )
                .verifyComplete();
    }

    @Test
    void updatePreferences_createsNew_whenNotExists() {
        NotificationPreferencesDto dto = NotificationPreferencesDto.builder()
                .userId("user-123")
                .emailEnabled(false)
                .pushEnabled(false)
                .build();

        when(repository.findByUserId("user-123")).thenReturn(Mono.empty());
        when(repository.save(any(NotificationPreferences.class))).thenAnswer(inv ->
            Mono.just(inv.getArgument(0))
        );

        StepVerifier.create(service.updatePreferences("user-123", dto))
                .expectNextMatches(dto_result ->
                    !dto_result.isEmailEnabled() &&
                    !dto_result.isPushEnabled()
                )
                .verifyComplete();
    }

    @Test
    void isChannelEnabled_returnsTrue_whenNoPreferences() {
        when(repository.findByUserId("user-123")).thenReturn(Mono.empty());

        StepVerifier.create(service.isChannelEnabled("user-123", "push"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isChannelEnabled_respectsPreference() {
        NotificationPreferences prefs = NotificationPreferences.builder()
                .pushEnabled(false)
                .emailEnabled(true)
                .build();

        when(repository.findByUserId("user-123")).thenReturn(Mono.just(prefs));

        StepVerifier.create(service.isChannelEnabled("user-123", "push"))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(service.isChannelEnabled("user-123", "email"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isNotificationTypeEnabled_respectsPreference() {
        NotificationPreferences prefs = NotificationPreferences.builder()
                .newOfferEnabled(false)
                .priceAlertEnabled(true)
                .build();

        when(repository.findByUserId("user-123")).thenReturn(Mono.just(prefs));

        StepVerifier.create(service.isNotificationTypeEnabled("user-123", "newoffer"))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(service.isNotificationTypeEnabled("user-123", "pricealert"))
                .expectNext(true)
                .verifyComplete();
    }
}