package auto.parto.repository.mongo_repository;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.Buyer;
import auto.parto.model.OtpDetails;
import auto.parto.model.Seller;
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

    @Autowired
    private SellerRepository sellerRepository;

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
                        .thenMany(Flux.range(0, 12)
                                .flatMap(ignored -> buyerRepository.recordFailedOtpAttempt(email, LocalDateTime.now()), 12)
                                .last())
                        .flatMap(saved -> buyerRepository.findBy(email, "email")))
                .assertNext(saved -> {
                    assertThat(saved.getOtpDetails().getFailedAttempts()).isEqualTo(5);
                    assertThat(saved.getOtpDetails().getLockedUntil()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void replacingBuyerChallengeInvalidatesThePreviouslyReadCode() {
        String email = "buyer-replacement-" + UUID.randomUUID() + "@example.test";
        LocalDateTime now = LocalDateTime.now();
        Buyer buyer = Buyer.builder().id(UUID.randomUUID().toString()).email(email).isActive(false)
                .otpDetails(OtpDetails.builder().otpHash(OtpCodeHasher.hashCode("123456"))
                        .purpose("LOGIN").identity(email).timestamp(now).expiresAt(now.plusMinutes(5)).build())
                .build();

        StepVerifier.create(buyerRepository.save(buyer)
                        .then(buyerRepository.updateGeneratedOtp(email, "654321", "email", now.plusMinutes(5)))
                        .then(buyerRepository.consumeValidOtp(email, "123456", LocalDateTime.now())))
                .expectNext(false).verifyComplete();
    }

    @Test
    void sellerChallengeReplacementAndFailureBurstAreSafe() {
        String email = "seller-security-" + UUID.randomUUID() + "@example.test";
        LocalDateTime now = LocalDateTime.now();
        Seller seller = Seller.builder().id(UUID.randomUUID().toString()).email(email).isActive(false)
                .otpDetails(OtpDetails.builder().otpHash(OtpCodeHasher.hashCode("123456"))
                        .purpose("LOGIN").identity(email).timestamp(now).expiresAt(now.plusMinutes(5)).build())
                .build();

        StepVerifier.create(sellerRepository.save(seller)
                        .then(sellerRepository.updateGeneratedOtp(email, "654321", "email", now.plusMinutes(5)))
                        .then(sellerRepository.consumeValidOtp(email, "123456", LocalDateTime.now())))
                .expectNext(false).verifyComplete();

        StepVerifier.create(Flux.range(0, 12)
                        .flatMap(ignored -> sellerRepository.recordFailedOtpAttempt(email, LocalDateTime.now()), 12)
                        .then(sellerRepository.findBy(email, "email")))
                .assertNext(saved -> {
                    assertThat(saved.getOtpDetails().getFailedAttempts()).isEqualTo(5);
                    assertThat(saved.getOtpDetails().getLockedUntil()).isNotNull();
                }).verifyComplete();
    }
}
