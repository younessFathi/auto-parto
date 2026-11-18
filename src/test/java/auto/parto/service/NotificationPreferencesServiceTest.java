package auto.parto.service;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.enums.NotificationType;
import auto.parto.persistence.NotificationPreferencesRepository;
import auto.parto.service.notification.NotificationPolicy;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationPreferencesServiceTest {
    private final NotificationPreferencesRepository repository = mock(NotificationPreferencesRepository.class);
    private final NotificationPreferencesService service = new NotificationPreferencesService(repository);
    private final String owner = UUID.randomUUID().toString();

    @Test
    void absentPreferencesMatchFullDtoDefaultsAndRequireBothDimensions() {
        when(repository.findByUserPublicId(UUID.fromString(owner))).thenReturn(Mono.empty());
        NotificationPreferencesDto defaults = service.getPreferences(owner).block();
        assertThat(defaults.isEmailEnabled()).isTrue();
        assertThat(defaults.isPushEnabled()).isTrue();
        assertThat(defaults.isSmsEnabled()).isFalse();
        assertThat(defaults.isMarketingEnabled()).isFalse();
        assertThat(service.canDeliver(owner, "SMS", "newoffer").block()).isFalse();
        assertThat(service.canDeliver(owner, "email", "marketing").block()).isFalse();
        assertThat(service.canDeliver(owner, "SSE", "SELLER_RESPONSE").block()).isTrue();
        assertThat(service.isChannelEnabled(owner, "sms").block()).isFalse();
        assertThat(service.isNotificationTypeEnabled(owner, "marketing").block()).isFalse();
    }

    @Test
    void allEnumAndLegacyTypesHaveExplicitCategoriesAndUnknownsRejectBeforeLookup() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(NotificationPolicy.category(type.name())).isEqualTo(NotificationPolicy.category(type.getCode()));
        }
        assertThat(NotificationPolicy.category("NEW_SEARCH_MATCH")).isEqualTo("searchmatch");
        assertThatThrownBy(() -> service.canDeliver(owner, "fax", "newoffer")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.canDeliver(owner, "push", "mystery")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.isNotificationTypeEnabled(owner, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,true,true", "true,false,false", "false,true,false", "false,false,false"})
    void storedChannelAndCategoryAreCombinedFromOneSnapshot(boolean push, boolean category, boolean allowed) {
        when(repository.findByUserPublicId(UUID.fromString(owner))).thenReturn(Mono.just(
                new auto.parto.persistence.NotificationPreferencesRecord(UUID.randomUUID(), UUID.fromString(owner),
                        true, push, false, category, true, true, true, false, null, null)));
        assertThat(service.canDeliver(owner, "FCM", "SELLER_RESPONSE").block()).isEqualTo(allowed);
        verify(repository, times(1)).findByUserPublicId(UUID.fromString(owner));
    }

    @Test
    void lookupFailureIsNotPermissionOrOptOut() {
        when(repository.findByUserPublicId(UUID.fromString(owner))).thenReturn(Mono.error(new IllegalStateException("unavailable")));
        assertThatThrownBy(() -> service.canDeliver(owner, "push", "newoffer").block()).isInstanceOf(IllegalStateException.class);
    }
}
