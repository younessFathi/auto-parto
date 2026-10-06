package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.time.Instant;

public interface BuyerIdentityOtpRepository {
    Mono<BuyerIdentityRecord> findByEmail(String email);
    Mono<BuyerIdentityRecord> create(String email, String firstName, String lastName, String phoneNumber,
                                     String otp, Instant expiresAt);
    Mono<BuyerIdentityRecord> replaceOtp(String email, String otp, Instant expiresAt);
    Mono<Boolean> consumeValidOtp(String email, String otp, Instant now);
    Mono<Void> recordFailedAttempt(String email, Instant now);
}
