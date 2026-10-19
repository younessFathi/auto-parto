package auto.parto.repository.mongo_repository;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.Buyer;
import auto.parto.model.OtpDetails;
import auto.parto.otp.OtpCodeHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MongoOtpSecurityIT extends BaseIntegrationTest {

    @Autowired
    private BuyerRepository buyerRepository;

    @Test
    void storesOnlyTheHashAndConsumesAChallengeExactlyOnce() {
        String email = "buyer-" + UUID.randomUUID() + "@example.test";
        LocalDateTime now = LocalDateTime.now();
        Buyer buyer = Buyer.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .isActive(false)
                .otpDetails(OtpDetails.builder()
                        .otpHash(OtpCodeHasher.hashCode("123456"))
                        .purpose("LOGIN")
                        .identity(email)
                        .timestamp(now)
                        .expiresAt(now.plusMinutes(5))
                        .build())
                .build();

        StepVerifier.create(buyerRepository.save(buyer)
                        .thenMany(Flux.range(0, 2)
                                .flatMap(ignored -> buyerRepository.consumeValidOtp(email, "123456", LocalDateTime.now()), 2)
                                .collectList()))
                .assertNext(results -> assertThat(results).containsExactlyInAnyOrder(true, false))
                .verifyComplete();

        StepVerifier.create(buyerRepository.findBy(email, "email"))
                .assertNext(saved -> {
                    assertThat(saved.getOtpDetails().getOtp()).isNull();
                    assertThat(saved.getOtpDetails().getOtpHash()).isNotEqualTo("123456");
                    assertThat(saved.getOtpDetails().getConsumedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void failedAttemptsAreAtomicAndLockAfterFiveFailures() {
        String email = "buyer-" + UUID.randomUUID() + "@example.test";
        LocalDateTime now = LocalDateTime.now();
        Buyer buyer = Buyer.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .isActive(false)
                .otpDetails(OtpDetails.builder()
                        .otpHash(OtpCodeHasher.hashCode("123456"))
                        .purpose("LOGIN")
                        .identity(email)
                        .timestamp(now)
                        .expiresAt(now.plusMinutes(5))
                        .build())
                .build();

        StepVerifier.create(buyerRepository.save(buyer)
                        .thenMany(Flux.range(0, 5)
                                .flatMap(ignored -> buyerRepository.recordFailedOtpAttempt(email, LocalDateTime.now()), 5)
                                .last())
                        .flatMap(saved -> buyerRepository.findBy(email, "email")))
                .assertNext(saved -> {
                    assertThat(saved.getOtpDetails().getFailedAttempts()).isEqualTo(5);
                    assertThat(saved.getOtpDetails().getLockedUntil()).isNotNull();
                })
                .verifyComplete();
    }
}
