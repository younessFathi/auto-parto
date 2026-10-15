package auto.parto.api;

import auto.parto.dto.PartRequestCreateRequest;
import auto.parto.dto.PartRequestResponse;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.PartRequestService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/part-requests")
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class PartRequestController {

    private final PartRequestService service;

    public PartRequestController(PartRequestService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<PartRequestResponse>> create(
            @Valid @RequestBody PartRequestCreateRequest request,
            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        return service.create(parsePrincipalId(principal), request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    public Flux<PartRequestResponse> findMine(Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        return service.findMine(parsePrincipalId(principal));
    }

    @PostMapping("/{publicId}/close")
    public Mono<PartRequestResponse> close(@PathVariable UUID publicId, Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        return service.close(parsePrincipalId(principal), publicId);
    }

    @PostMapping("/{publicId}/cancel")
    public Mono<PartRequestResponse> cancel(@PathVariable UUID publicId, Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        return service.cancel(parsePrincipalId(principal), publicId);
    }

    private static UUID parsePrincipalId(CanonicalPrincipal principal) {
        try {
            return UUID.fromString(principal.getId());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Authenticated buyer identity must be a UUID", exception);
        }
    }
}
