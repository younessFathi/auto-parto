package auto.parto.api;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.service.NotificationPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferencesController {

    private final NotificationPreferencesService preferencesService;

    @GetMapping
    public Mono<ResponseEntity<NotificationPreferencesDto>> getPreferences(Authentication authentication) {
        String userId = authentication.getName();
        return preferencesService.getPreferences(userId)
                .map(ResponseEntity::ok);
    }

    @PutMapping
    public Mono<ResponseEntity<NotificationPreferencesDto>> updatePreferences(
            Authentication authentication,
            @Valid @RequestBody NotificationPreferencesDto dto) {
        String userId = authentication.getName();
        dto.setUserId(userId);
        return preferencesService.updatePreferences(userId, dto)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/channel/{channel}")
    public Mono<ResponseEntity<Boolean>> isChannelEnabled(
            Authentication authentication,
            @PathVariable String channel) {
        String userId = authentication.getName();
        return preferencesService.isChannelEnabled(userId, channel)
                .map(enabled -> ResponseEntity.ok(enabled));
    }

    @GetMapping("/type/{type}")
    public Mono<ResponseEntity<Boolean>> isNotificationTypeEnabled(
            Authentication authentication,
            @PathVariable String type) {
        String userId = authentication.getName();
        return preferencesService.isNotificationTypeEnabled(userId, type)
                .map(enabled -> ResponseEntity.ok(enabled));
    }
}