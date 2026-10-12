package auto.parto.api;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
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
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        return preferencesService.getPreferences(principal.getId(), principal.getEmail())
                .map(ResponseEntity::ok);
    }

    @PutMapping
    public Mono<ResponseEntity<NotificationPreferencesDto>> updatePreferences(
            Authentication authentication,
            @Valid @RequestBody NotificationPreferencesDto dto) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        String userId = principal.getId();
        dto.setUserId(userId);
        return preferencesService.updatePreferences(userId, principal.getEmail(), dto)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/channel/{channel}")
    public Mono<ResponseEntity<Boolean>> isChannelEnabled(
            Authentication authentication,
            @PathVariable String channel) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        return preferencesService.isChannelEnabled(principal.getId(), principal.getEmail(), channel)
                .map(enabled -> ResponseEntity.ok(enabled));
    }

    @GetMapping("/type/{type}")
    public Mono<ResponseEntity<Boolean>> isNotificationTypeEnabled(
            Authentication authentication,
            @PathVariable String type) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        return preferencesService.isNotificationTypeEnabled(principal.getId(), principal.getEmail(), type)
                .map(enabled -> ResponseEntity.ok(enabled));
    }

}
