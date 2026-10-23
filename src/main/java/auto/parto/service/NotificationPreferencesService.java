package auto.parto.service;

import auto.parto.dto.NotificationPreferencesDto;
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
    public Mono<Boolean> isChannelEnabled(String userId, String channel) { return find(userId).map(record -> channelEnabled(record, channel)).defaultIfEmpty(true); }
    public Mono<Boolean> isChannelEnabled(String userId, String ignoredEmail, String channel) { return isChannelEnabled(userId, channel); }
    public Mono<Boolean> isNotificationTypeEnabled(String userId, String type) { return find(userId).map(record -> typeEnabled(record, type)).defaultIfEmpty(true); }
    public Mono<Boolean> isNotificationTypeEnabled(String userId, String ignoredEmail, String type) { return isNotificationTypeEnabled(userId, type); }
    private Mono<NotificationPreferencesRecord> find(String userId) { return repository.findByUserPublicId(requireUuid(userId)); }
    private Mono<NotificationPreferencesDto> save(String userId, NotificationPreferencesDto dto) { return repository.save(requireUuid(userId), new NotificationPreferencesRecord(null, requireUuid(userId), dto.isEmailEnabled(), dto.isPushEnabled(), dto.isSmsEnabled(), dto.isNewOfferEnabled(), dto.isPriceAlertEnabled(), dto.isSearchMatchEnabled(), dto.isOrderUpdateEnabled(), dto.isMarketingEnabled(), null, null)).map(this::toDto); }
    private static boolean channelEnabled(NotificationPreferencesRecord p, String channel) { return switch (channel.toLowerCase()) { case "email" -> p.emailEnabled(); case "push" -> p.pushEnabled(); case "sms" -> p.smsEnabled(); default -> true; }; }
    private static boolean typeEnabled(NotificationPreferencesRecord p, String type) { return switch (type.toLowerCase()) { case "newoffer" -> p.newOfferEnabled(); case "pricealert" -> p.priceAlertEnabled(); case "searchmatch" -> p.searchMatchEnabled(); case "orderupdate" -> p.orderUpdateEnabled(); case "marketing" -> p.marketingEnabled(); default -> true; }; }
    private static UUID requireUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new IllegalArgumentException("canonical user ID must be a UUID", e); } }
    private NotificationPreferencesDto toDto(NotificationPreferencesRecord p) { return NotificationPreferencesDto.builder().userId(p.userPublicId().toString()).emailEnabled(p.emailEnabled()).pushEnabled(p.pushEnabled()).smsEnabled(p.smsEnabled()).newOfferEnabled(p.newOfferEnabled()).priceAlertEnabled(p.priceAlertEnabled()).searchMatchEnabled(p.searchMatchEnabled()).orderUpdateEnabled(p.orderUpdateEnabled()).marketingEnabled(p.marketingEnabled()).build(); }
    private static NotificationPreferencesDto defaults(String id) { return NotificationPreferencesDto.builder().userId(id).emailEnabled(true).pushEnabled(true).smsEnabled(false).newOfferEnabled(true).priceAlertEnabled(true).searchMatchEnabled(true).orderUpdateEnabled(true).marketingEnabled(false).build(); }
}
