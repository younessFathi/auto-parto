package auto.parto.repository;

import auto.parto.model.Buyer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ICustomerRepository {
    Flux<Buyer> findAll();
    Mono<Buyer> findById(String id);
    Mono<Buyer> save(Buyer user);
    Mono<Buyer> findBy(String phoneNumber, String field);
    Mono<Boolean> exists(String phoneNumber, String field);
    Mono<Boolean> existsById(String id);
    Mono<Buyer> updateGeneratedOtp(String phoneNumber, String sentOtp, String field, LocalDateTime expiresAt);
    Mono<Boolean> consumeValidOtp(String email, String otp, LocalDateTime now);
    Mono<Buyer> recordFailedOtpAttempt(String email, LocalDateTime now);
    Mono<Buyer> deactivateCustomer(String id);
    Mono<Buyer> findByIdAndIsActiveTrue(String id);
}
