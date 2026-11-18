package auto.parto.service;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.service.notification.NotificationPolicy;
import auto.parto.persistence.NotificationPreferencesRecord;
import auto.parto.persistence.NotificationPreferencesRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class NotificationPreferencesService {
    private final NotificationPreferencesRepository repository;
    public NotificationPreferencesService(NotificationPreferencesRepository repository) { this.repository = repository; }
    public Mono<NotificationPreferencesDto> getPreferences(String userId) { return find(userId).map(this::toDto).switchIfEmpty(Mono.fromSupplier(() -> defaults(userId))); }
    public Mono<NotificationPreferencesDto> getPreferences(String userId, String ignoredEmail) { return getPreferences(userId); }
    public Mono<NotificationPreferencesDto> updatePreferences(String userId, NotificationPreferencesDto dto) { return save(userId, dto); }
    public Mono<NotificationPreferencesDto> updatePreferences(String userId, String ignoredEmail, NotificationPreferencesDto dto) { return save(userId, dto); }
    public Mono<Boolean> isChannelEnabled(String userId, String channel) { String known = NotificationPolicy.channel(channel); return getPreferences(userId).map(p -> channelEnabled(p, known)); }
    public Mono<Boolean> isChannelEnabled(String userId, String ignoredEmail, String channel) { return isChannelEnabled(userId, channel); }
    public Mono<Boolean> isNotificationTypeEnabled(String userId, String type) { String known = NotificationPolicy.category(type); return getPreferences(userId).map(p -> typeEnabled(p, known)); }
    public Mono<Boolean> isNotificationTypeEnabled(String userId, String ignoredEmail, String type) { return isNotificationTypeEnabled(userId, type); }
    private Mono<NotificationPreferencesRecord> find(String userId) { return repository.findByUserPublicId(requireUuid(userId)); }
    private Mono<NotificationPreferencesDto> save(String userId, NotificationPreferencesDto dto) { return repository.save(requireUuid(userId), new NotificationPreferencesRecord(null, requireUuid(userId), dto.isEmailEnabled(), dto.isPushEnabled(), dto.isSmsEnabled(), dto.isNewOfferEnabled(), dto.isPriceAlertEnabled(), dto.isSearchMatchEnabled(), dto.isOrderUpdateEnabled(), dto.isMarketingEnabled(), null, null)).map(this::toDto); }
    public Mono<Boolean> canDeliver(String userId, String channel, String type) {
        String knownChannel = NotificationPolicy.channel(channel);
        String category = NotificationPolicy.category(type);
        return getPreferences(userId).map(p -> channelEnabled(p, knownChannel) && typeEnabled(p, category));
    }
    private static boolean channelEnabled(NotificationPreferencesDto p, String channel) {
        return switch (channel) {
            case "email" -> p.isEmailEnabled(); case "push" -> p.isPushEnabled(); case "sms" -> p.isSmsEnabled();
            default -> throw new IllegalArgumentException("unsupported notification channel");
        };
    }
    private static boolean typeEnabled(NotificationPreferencesDto p, String type) {
        return switch (type) {
            case "newoffer" -> p.isNewOfferEnabled(); case "pricealert" -> p.isPriceAlertEnabled();
            case "searchmatch" -> p.isSearchMatchEnabled(); case "orderupdate" -> p.isOrderUpdateEnabled();
            case "marketing" -> p.isMarketingEnabled();
            default -> throw new IllegalArgumentException("unsupported notification type");
        };
    }
    private static UUID requireUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new IllegalArgumentException("canonical user ID must be a UUID", e); } }
    private NotificationPreferencesDto toDto(NotificationPreferencesRecord p) { return NotificationPreferencesDto.builder().userId(p.userPublicId().toString()).emailEnabled(p.emailEnabled()).pushEnabled(p.pushEnabled()).smsEnabled(p.smsEnabled()).newOfferEnabled(p.newOfferEnabled()).priceAlertEnabled(p.priceAlertEnabled()).searchMatchEnabled(p.searchMatchEnabled()).orderUpdateEnabled(p.orderUpdateEnabled()).marketingEnabled(p.marketingEnabled()).build(); }
    private static NotificationPreferencesDto defaults(String id) { return NotificationPreferencesDto.builder().userId(id).emailEnabled(true).pushEnabled(true).smsEnabled(false).newOfferEnabled(true).priceAlertEnabled(true).searchMatchEnabled(true).orderUpdateEnabled(true).marketingEnabled(false).build(); }
}
