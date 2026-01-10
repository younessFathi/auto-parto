package auto.parto.utils;

import auto.parto.service.BuyerService;
import auto.parto.service.SellerService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SecurityHelper {
    private final BuyerService buyerService;
    private final SellerService sellerService;


    public SecurityHelper(BuyerService buyerService, SellerService sellerService){
        this.buyerService = buyerService;
        this.sellerService = sellerService;
    }
    /**
     * Validates that the authenticated user exists and is active.
     */
    public Mono<Boolean> validateSender(Authentication authentication) {
        String email = authentication.getName(); // JWT Subject is email

        boolean isBuyer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("BUYER"));
        boolean isSeller = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("SELLER"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("ADMIN"));

        if (isAdmin) {
            return Mono.just(true);
        }

        if (isBuyer) {
            return buyerService.findBuyerByEmail(email)
                    .map(dto -> Boolean.TRUE.equals(dto.getIsActive()))
                    .defaultIfEmpty(false);
        }

        if (isSeller) {
            // Assuming SellerService has a similar method to BuyerService
            return sellerService.findSellerByEmail(email)
                    .map(dto -> Boolean.TRUE.equals(dto.getIsActive()))
                    .defaultIfEmpty(false);
        }

        return Mono.just(false);
    }

}
