package auto.parto.api;

import auto.parto.dto.BuyerProfileResponse;
import auto.parto.dto.BuyerProfileUpdateRequest;
import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.service.iservice.IBuyerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.buyer-identity.enabled:false}' == 'true'")
@RequestMapping("/customers/profile")
public class BuyerProfileController {

    private final IBuyerProfileService profileService;

    @PutMapping
    public Mono<BuyerProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody BuyerProfileUpdateRequest request) {
        CanonicalPrincipal principal = canonicalBuyer(authentication);
        UUID userPublicId;
        try {
            userPublicId = UUID.fromString(principal.getId());
        } catch (IllegalArgumentException exception) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Canonical buyer ID must be a UUID", exception));
        }

        return profileService.update(userPublicId, request.firstName(), request.lastName(),
                        request.phoneNumber(), request.pictureUrl())
                .map(BuyerProfileController::toResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Buyer profile not found")));
    }

    private static CanonicalPrincipal canonicalBuyer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CanonicalPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Canonical authentication is required");
        }
        if (!"ROLE_BUYER".equals(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Buyer role is required");
        }
        return principal;
    }

    private static BuyerProfileResponse toResponse(BuyerProfileRecord profile) {
        return new BuyerProfileResponse(profile.publicId(), profile.userPublicId(), profile.firstName(),
                profile.lastName(), profile.phoneNumber(), profile.pictureUrl());
    }
}
