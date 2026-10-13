package auto.parto.service;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.model.NotificationPreferences;
import auto.parto.persistence.NotificationPreferencesRecord;
import auto.parto.repository.NotificationPreferencesRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository repository;
    private final auto.parto.persistence.NotificationPreferencesRepository postgresqlRepository;

    public NotificationPreferencesService(NotificationPreferencesRepository repository) {
        this.repository = repository;
        this.postgresqlRepository = null;
    }

    @Autowired
    public NotificationPreferencesService(ObjectProvider<NotificationPreferencesRepository> repositoryProvider,
                                          ObjectProvider<auto.parto.persistence.NotificationPreferencesRepository> provider) {
        this.repository = repositoryProvider.getIfAvailable();
        this.postgresqlRepository = provider.getIfAvailable();
    }

    public Mono<NotificationPreferencesDto> getPreferences(String userId) {
        return preferencesFor(userId, null)
                .map(this::toDto)
                .switchIfEmpty(Mono.just(createDefaultPreferences(userId)));
    }

    public Mono<NotificationPreferencesDto> getPreferences(String userId, String legacyEmail) {
        return preferencesFor(userId, legacyEmail)
                .map(this::toDto)
                .switchIfEmpty(Mono.just(createDefaultPreferences(userId)));
    }

    public Mono<NotificationPreferencesDto> updatePreferences(String userId, NotificationPreferencesDto dto) {
        return updatePreferencesFrom(preferencesFor(userId, null), userId, dto);
    }

    public Mono<NotificationPreferencesDto> updatePreferences(String userId, String legacyEmail,
                                                               NotificationPreferencesDto dto) {
        return updatePreferencesFrom(preferencesFor(userId, legacyEmail), userId, dto);
    }

    private Mono<NotificationPreferencesDto> updatePreferencesFrom(Mono<NotificationPreferences> existingPreferences,
                                                                     String userId, NotificationPreferencesDto dto) {
        return existingPreferences
                .flatMap(existing -> {
                    existing.setEmailEnabled(dto.isEmailEnabled());
                    existing.setPushEnabled(dto.isPushEnabled());
                    existing.setSmsEnabled(dto.isSmsEnabled());
                    existing.setNewOfferEnabled(dto.isNewOfferEnabled());
                    existing.setPriceAlertEnabled(dto.isPriceAlertEnabled());
                    existing.setSearchMatchEnabled(dto.isSearchMatchEnabled());
                    existing.setOrderUpdateEnabled(dto.isOrderUpdateEnabled());
                    existing.setMarketingEnabled(dto.isMarketingEnabled());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return saveCanonical(existing, userId);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    NotificationPreferences preferences = NotificationPreferences.builder()
                            .userId(userId)
                            .emailEnabled(dto.isEmailEnabled())
                            .pushEnabled(dto.isPushEnabled())
                            .smsEnabled(dto.isSmsEnabled())
                            .newOfferEnabled(dto.isNewOfferEnabled())
                            .priceAlertEnabled(dto.isPriceAlertEnabled())
                            .searchMatchEnabled(dto.isSearchMatchEnabled())
                            .orderUpdateEnabled(dto.isOrderUpdateEnabled())
                            .marketingEnabled(dto.isMarketingEnabled())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return saveCanonical(preferences, userId);
                }))
                .map(this::toDto);
    }

    public Mono<Boolean> isChannelEnabled(String userId, String channel) {
        return preferencesFor(userId, null)
                .map(prefs -> {
                    return switch (channel.toLowerCase()) {
                        case "email" -> prefs.isEmailEnabled();
                        case "push" -> prefs.isPushEnabled();
                        case "sms" -> prefs.isSmsEnabled();
                        default -> true;
                    };
                })
                .defaultIfEmpty(true);
    }

    public Mono<Boolean> isChannelEnabled(String userId, String legacyEmail, String channel) {
        return preferencesFor(userId, legacyEmail)
                .map(prefs -> channelEnabled(prefs, channel))
                .defaultIfEmpty(true);
    }

    public Mono<Boolean> isNotificationTypeEnabled(String userId, String type) {
        return preferencesFor(userId, null)
                .map(prefs -> {
                    return switch (type.toLowerCase()) {
                        case "newoffer" -> prefs.isNewOfferEnabled();
                        case "pricealert" -> prefs.isPriceAlertEnabled();
                        case "searchmatch" -> prefs.isSearchMatchEnabled();
                        case "orderupdate" -> prefs.isOrderUpdateEnabled();
                        case "marketing" -> prefs.isMarketingEnabled();
                        default -> true;
                    };
                })
                .defaultIfEmpty(true);
    }

    public Mono<Boolean> isNotificationTypeEnabled(String userId, String legacyEmail, String type) {
        return preferencesFor(userId, legacyEmail)
                .map(prefs -> notificationTypeEnabled(prefs, type))
                .defaultIfEmpty(true);
    }

    private Mono<NotificationPreferences> preferencesFor(String userId, String legacyEmail) {
        if (postgresqlRepository != null) {
            return postgresqlUser(userId).flatMap(postgresqlRepository::findByUserPublicId)
                    .map(this::fromRecord);
        }
        Mono<NotificationPreferences> canonical = repository == null
                ? Mono.empty() : repository.findByUserId(userId);
        if (legacyEmail == null || legacyEmail.equals(userId)) {
            return canonical;
        }
        return canonical.switchIfEmpty(repository == null ? Mono.empty() : repository.findByUserId(legacyEmail)
                .flatMap(legacy -> {
                    legacy.setUserId(userId);
                    legacy.setUpdatedAt(LocalDateTime.now());
                    return repository.save(legacy);
                }));
    }

    private Mono<NotificationPreferences> saveCanonical(NotificationPreferences preferences, String userId) {
        if (postgresqlRepository == null) {
            if (repository == null) {
                return Mono.error(new IllegalStateException("No notification preferences persistence is configured"));
            }
            return repository.save(preferences);
        }
        return postgresqlUser(userId)
                .flatMap(owner -> postgresqlRepository.save(owner, toRecord(preferences)))
                .map(this::fromRecord);
    }

    private boolean channelEnabled(NotificationPreferences prefs, String channel) {
        return switch (channel.toLowerCase()) {
            case "email" -> prefs.isEmailEnabled();
            case "push" -> prefs.isPushEnabled();
            case "sms" -> prefs.isSmsEnabled();
            default -> true;
        };
    }

    private boolean notificationTypeEnabled(NotificationPreferences prefs, String type) {
        return switch (type.toLowerCase()) {
            case "newoffer" -> prefs.isNewOfferEnabled();
            case "pricealert" -> prefs.isPriceAlertEnabled();
            case "searchmatch" -> prefs.isSearchMatchEnabled();
            case "orderupdate" -> prefs.isOrderUpdateEnabled();
            case "marketing" -> prefs.isMarketingEnabled();
            default -> true;
        };
    }

    private NotificationPreferencesDto toDto(NotificationPreferences prefs) {
        return NotificationPreferencesDto.builder()
                .userId(prefs.getUserId())
                .emailEnabled(prefs.isEmailEnabled())
                .pushEnabled(prefs.isPushEnabled())
                .smsEnabled(prefs.isSmsEnabled())
                .newOfferEnabled(prefs.isNewOfferEnabled())
                .priceAlertEnabled(prefs.isPriceAlertEnabled())
                .searchMatchEnabled(prefs.isSearchMatchEnabled())
                .orderUpdateEnabled(prefs.isOrderUpdateEnabled())
                .marketingEnabled(prefs.isMarketingEnabled())
                .build();
    }

    private NotificationPreferencesDto createDefaultPreferences(String userId) {
        return NotificationPreferencesDto.builder()
                .userId(userId)
                .emailEnabled(true)
                .pushEnabled(true)
                .smsEnabled(false)
                .newOfferEnabled(true)
                .priceAlertEnabled(true)
                .searchMatchEnabled(true)
                .orderUpdateEnabled(true)
                .marketingEnabled(false)
                .build();
    }

    private Mono<java.util.UUID> postgresqlUser(String userId) {
        try {
            return Mono.just(java.util.UUID.fromString(userId));
        } catch (IllegalArgumentException exception) {
            return Mono.empty();
        }
    }

    private NotificationPreferences fromRecord(NotificationPreferencesRecord record) {
        return NotificationPreferences.builder().id(record.publicId().toString())
                .userId(record.userPublicId().toString()).emailEnabled(record.emailEnabled())
                .pushEnabled(record.pushEnabled()).smsEnabled(record.smsEnabled())
                .newOfferEnabled(record.newOfferEnabled()).priceAlertEnabled(record.priceAlertEnabled())
                .searchMatchEnabled(record.searchMatchEnabled()).orderUpdateEnabled(record.orderUpdateEnabled())
                .marketingEnabled(record.marketingEnabled()).build();
    }

    private NotificationPreferencesRecord toRecord(NotificationPreferences preferences) {
        java.util.UUID publicId = null;
        if (preferences.getId() != null) {
            try { publicId = java.util.UUID.fromString(preferences.getId()); } catch (IllegalArgumentException ignored) { }
        }
        return new NotificationPreferencesRecord(publicId, null, preferences.isEmailEnabled(),
                preferences.isPushEnabled(), preferences.isSmsEnabled(), preferences.isNewOfferEnabled(),
                preferences.isPriceAlertEnabled(), preferences.isSearchMatchEnabled(),
                preferences.isOrderUpdateEnabled(), preferences.isMarketingEnabled(), null, null);
    }
}
