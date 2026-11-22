package auto.parto.api;

import auto.parto.dto.WalletFundingRequest;
import auto.parto.dto.WalletFundingResponse;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.WalletFundingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/admin/wallet-funding")
public class WalletFundingController {
    private final WalletFundingService service;

    public WalletFundingController(WalletFundingService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<WalletFundingResponse> confirm(@Valid @RequestBody WalletFundingRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_ADMIN");
        return service.confirm(UUID.fromString(principal.getId()), request, idempotencyKey);
    }
}
