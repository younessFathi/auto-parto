package auto.parto.repository;

import auto.parto.model.Buyer;
import auto.parto.model.Seller;
import reactor.core.publisher.Mono;

import java.util.List;
import java.time.LocalDateTime;

public interface ISellerRepository {
    Mono<Seller> save(Seller seller);
    Mono<Seller> getSellerById(String id);
    Mono<Seller> findBy(String value, String field);
    Mono<Boolean> exists(String email, String field);
    Mono<Seller> updateGeneratedOtp(String email, String sentOtp, String field, LocalDateTime expiresAt);
    Mono<Boolean> consumeValidOtp(String email, String otp, LocalDateTime now);
    Mono<Seller> recordFailedOtpAttempt(String email, LocalDateTime now);
    Mono<List<String>> findMatchingSellers();
    Mono<Seller> addFunds(String id, long amountMinor);
}
