package auto.parto.utils;

import auto.parto.enums.CustomerType;
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
    public Mono<Boolean> validateAuthentication(Authentication authentication) {
        String email = authentication.getName(); // JWT Subject is email

        boolean isBuyer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains(CustomerType.BUYER.name()));
        boolean isSeller = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains(CustomerType.SELLER.name()));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains(CustomerType.ADMIN.name()));

        if (isAdmin) {
            return Mono.just(true);
        }

        if (isBuyer) {
            return buyerService.checkIfBuyerExistUsingEmail(email);
        }

        if (isSeller) {
            return sellerService.checkIfSellerExistUsingEmail(email);
        }

        return Mono.just(false);
    }

    public Mono<Boolean> validateSeller(Authentication authentication){
        String email = authentication.getName();
        boolean isSeller = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains(CustomerType.SELLER.name()));
        if (isSeller){
          return sellerService.checkIfSellerExistUsingEmail(email);
        }
        return Mono.just(false);
    }

    public Mono<Boolean> validateBuyer(Authentication authentication){
        String email = authentication.getName();
        boolean isBuyer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains(CustomerType.BUYER.name()));
        if (isBuyer){
            return buyerService.checkIfBuyerExistUsingEmail(email);
        }
        return Mono.just(false);
    }

    public Mono<Boolean> validateAdmin(Authentication authentication){
        String email = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains(CustomerType.ADMIN.name()));
        return Mono.just(isAdmin);
    }

}
