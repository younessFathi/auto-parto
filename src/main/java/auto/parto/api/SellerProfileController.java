package auto.parto.api;

import auto.parto.dto.SellerProfileResponse;
import auto.parto.dto.SellerProfileUpdateRequest;
import auto.parto.persistence.SellerProfileRecord;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.iservice.ISellerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.enabled:false}' == 'true' && "
        + "'${auto.parto.persistence.postgresql.identity.enabled:false}' == 'true'")
@RequestMapping("/seller/profile")
public class SellerProfileController {

    private final ISellerProfileService profileService;

    @PutMapping
    @PreAuthorize("hasRole('SELLER')")
    public Mono<SellerProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody SellerProfileUpdateRequest request) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        UUID userPublicId;
        try {
            userPublicId = UUID.fromString(principal.getId());
        } catch (IllegalArgumentException exception) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Canonical seller ID must be a UUID", exception));
        }

        return profileService.update(userPublicId, request.firstName(), request.lastName(),
                        request.phoneNumber(), request.partType(), request.description())
                .map(SellerProfileController::toResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Seller profile not found")));
    }

    private static SellerProfileResponse toResponse(SellerProfileRecord profile) {
        return new SellerProfileResponse(profile.publicId(), profile.userPublicId(), profile.firstName(),
                profile.lastName(), profile.phoneNumber(), profile.partType(), profile.description());
    }
}
