package auto.parto.service;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.model.NotificationPreferences;
import auto.parto.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository repository;

    public Mono<NotificationPreferencesDto> getPreferences(String userId) {
        return repository.findByUserId(userId)
                .map(this::toDto)
                .switchIfEmpty(Mono.just(createDefaultPreferences(userId)));
    }

    public Mono<NotificationPreferencesDto> updatePreferences(String userId, NotificationPreferencesDto dto) {
        return repository.findByUserId(userId)
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
                    return repository.save(existing);
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
                    return repository.save(preferences);
                }))
                .map(this::toDto);
    }

    public Mono<Boolean> isChannelEnabled(String userId, String channel) {
        return repository.findByUserId(userId)
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

    public Mono<Boolean> isNotificationTypeEnabled(String userId, String type) {
        return repository.findByUserId(userId)
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
}