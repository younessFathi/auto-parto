package auto.parto.api;

import auto.parto.dto.BuyerDto;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.iservice.IBuyerService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping({"/api/v1/buyers", "/customers"})
@AllArgsConstructor
public class BuyerController {
    private final IBuyerService buyerService;

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('BUYER')")
    public Mono<BuyerDto> deactivateSeller(@PathVariable String id, Authentication authentication) {
        CanonicalSecurity.requireOwner(authentication, id, "ROLE_BUYER");
        return buyerService.deactivateBuyer(id);
    }
}
