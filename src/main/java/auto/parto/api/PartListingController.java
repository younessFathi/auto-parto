package auto.parto.api;

import auto.parto.dto.PartListingCreateRequest;
import auto.parto.dto.PartListingPageResponse;
import auto.parto.dto.PartListingResponse;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.PartListingService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/part-listings")
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class PartListingController {
    private final PartListingService service;
    public PartListingController(PartListingService service) { this.service = service; }

    @PostMapping
    public Mono<ResponseEntity<PartListingResponse>> create(@Valid @RequestBody PartListingCreateRequest request,
                                                              Authentication authentication) {
        return service.create(id(CanonicalSecurity.requireRole(authentication, "ROLE_SELLER")), request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    public Mono<PartListingPageResponse> findMine(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size,
                                                   Authentication authentication) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid page or size");
        }
        return service.findMine(id(CanonicalSecurity.requireRole(authentication, "ROLE_SELLER")), page, size);
    }

    @PostMapping("/{publicId}/transition")
    public Mono<PartListingResponse> transition(@PathVariable UUID publicId, @RequestParam String status,
                                                 Authentication authentication) {
        return service.transition(id(CanonicalSecurity.requireRole(authentication, "ROLE_SELLER")), publicId, status);
    }

    private static UUID id(CanonicalPrincipal principal) {
        try {
            return UUID.fromString(principal.getId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Canonical seller identity is invalid");
        }
    }
}
