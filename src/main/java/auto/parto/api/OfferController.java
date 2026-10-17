package auto.parto.api;

import auto.parto.dto.CreateOfferRequest;
import auto.parto.dto.OfferResponse;
import auto.parto.dto.UpdateOfferStatusRequest;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.PostgresqlOfferService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/offers")
public class OfferController {
    private final PostgresqlOfferService service;

    public OfferController(PostgresqlOfferService service) {
        this.service = service;
    }

    @PostMapping("/requests/{requestId}")
    @PreAuthorize("hasRole('SELLER')")
    public Mono<OfferResponse> createForRequest(@PathVariable UUID requestId,
                                                @Valid @RequestBody CreateOfferRequest request,
                                                Authentication authentication) {
        CanonicalPrincipal seller = CanonicalSecurity.requireRole(authentication, "ROLE_SELLER");
        return service.createForRequest(UUID.fromString(seller.getId()), requestId, request);
    }

    @PostMapping("/listings/{listingId}")
    @PreAuthorize("hasRole('BUYER')")
    public Mono<OfferResponse> createForListing(@PathVariable UUID listingId,
                                                @Valid @RequestBody CreateOfferRequest request,
                                                Authentication authentication) {
        CanonicalPrincipal buyer = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        return service.createForListing(UUID.fromString(buyer.getId()), listingId, request);
    }

    @GetMapping("/{offerId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<OfferResponse> get(@PathVariable UUID offerId, Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        return service.findForActor(offerId, UUID.fromString(principal.getId()));
    }

    @PatchMapping("/{offerId}/status")
    @PreAuthorize("isAuthenticated()")
    public Mono<OfferResponse> updateStatus(@PathVariable UUID offerId,
                                             @Valid @RequestBody UpdateOfferStatusRequest request,
                                             Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        return service.updateStatus(offerId, UUID.fromString(principal.getId()), request);
    }
}
