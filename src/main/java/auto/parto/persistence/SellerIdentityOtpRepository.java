package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.time.Instant;

public interface SellerIdentityOtpRepository {

    Mono<SellerIdentityRecord> findByEmail(String email);

    Mono<SellerIdentityRecord> create(String email, String firstName, String lastName,
                                      String otp, Instant expiresAt);

    Mono<SellerIdentityRecord> replaceOtp(String email, String otp, Instant expiresAt);

    Mono<Boolean> consumeValidOtp(String email, String otp, Instant now);

    Mono<Void> recordFailedAttempt(String email, Instant now);
}
