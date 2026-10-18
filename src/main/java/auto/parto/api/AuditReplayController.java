package auto.parto.api;

import auto.parto.audit.AuditPublisher;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit")
@ConditionalOnBean(AuditPublisher.class)
public class AuditReplayController {

    private final AuditPublisher publisher;

    public AuditReplayController(AuditPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/{eventId}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> replay(
            @PathVariable UUID eventId,
            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_ADMIN");
        return publisher.replay(eventId, principal.getId()).thenReturn(ResponseEntity.accepted().build());
    }
}
