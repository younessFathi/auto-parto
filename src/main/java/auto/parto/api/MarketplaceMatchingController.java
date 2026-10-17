package auto.parto.api;

import auto.parto.dto.PartListingMatchResponse;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.MarketplaceMatchingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/part-requests")
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class MarketplaceMatchingController {

    private final MarketplaceMatchingService service;

    public MarketplaceMatchingController(MarketplaceMatchingService service) {
        this.service = service;
    }

    @GetMapping("/{publicId}/matches")
    @PreAuthorize("hasRole('BUYER')")
    public Mono<PartListingMatchResponse> findMatches(@PathVariable UUID publicId,
                                                       @RequestParam(defaultValue = "20") int limit,
                                                       Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        MarketplaceMatchingService.validateLimit(limit);
        return service.findMatches(UUID.fromString(principal.getId()), publicId, limit);
    }
}
