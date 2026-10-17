package auto.parto.api;

import auto.parto.dto.PartRequestCreateRequest;
import auto.parto.dto.PartRequestResponse;
import auto.parto.dto.PartRequestPageResponse;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.PartRequestService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/part-requests")
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class PartRequestController {

    private static final int MAX_LIMIT = 100;

    private final PartRequestService service;

    public PartRequestController(PartRequestService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public Mono<ResponseEntity<PartRequestResponse>> create(
            @Valid @RequestBody PartRequestCreateRequest request,
            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        return service.create(parsePrincipalId(principal), request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('BUYER')")
    public Mono<PartRequestPageResponse> findMine(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        validateLimit(limit);
        PartRequestService.validateCursor(cursor);
        return service.findMine(parsePrincipalId(principal), cursor, limit);
    }

    @PostMapping("/{publicId}/close")
    @PreAuthorize("hasRole('BUYER')")
    public Mono<PartRequestResponse> close(@PathVariable UUID publicId, Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        return service.close(parsePrincipalId(principal), publicId);
    }

    @PostMapping("/{publicId}/cancel")
    @PreAuthorize("hasRole('BUYER')")
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

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
    }
}
