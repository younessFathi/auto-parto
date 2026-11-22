package auto.parto.service;

import auto.parto.dto.WalletFundingRequest;
import auto.parto.enums.Currency;
import auto.parto.persistence.WalletFundingRepository;
import auto.parto.persistence.WalletFundingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.*;

class WalletFundingServiceTest {
    private final WalletFundingRepository repository = mock(WalletFundingRepository.class);
    private final ObjectProvider<WalletFundingRepository> provider = mock(ObjectProvider.class);

    @Test
    void delegatesCanonicalValuesAndReturnsPublicRecordedResult() {
        UUID admin = UUID.randomUUID(), owner = UUID.randomUUID(), funding = UUID.randomUUID();
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.confirm(admin, owner, 1234, "MAD", "manual-proof", "key"))
                .thenReturn(Mono.just(new WalletFundingResult(funding, "manual-proof", owner, 1234, "MAD",
                        UUID.randomUUID(), UUID.randomUUID(), 1234, 1)));
        StepVerifier.create(new WalletFundingService(provider).confirm(admin,
                        new WalletFundingRequest(owner, 1234L, Currency.MAD, "manual-proof", true), "key"))
                .expectNextMatches(response -> response.fundingPublicId().equals(funding)
                        && response.balanceAfterMinor() == 1234 && response.currency().equals("MAD"))
                .verifyComplete();
    }

    @Test
    void refusesUnconfirmedInstructionBeforePersistence() {
        when(provider.getIfAvailable()).thenReturn(repository);
        StepVerifier.create(new WalletFundingService(provider).confirm(UUID.randomUUID(),
                        new WalletFundingRequest(UUID.randomUUID(), 100L, Currency.MAD, "proof", false), "key"))
                .expectError(IllegalArgumentException.class).verify();
        verifyNoInteractions(repository);
    }

    @Test
    void disabledWalletBoundaryDoesNotPretendFundingSucceeded() {
        StepVerifier.create(new WalletFundingService(provider).confirm(UUID.randomUUID(),
                        new WalletFundingRequest(UUID.randomUUID(), 100L, Currency.MAD, "proof", true), "key"))
                .expectError(IllegalStateException.class).verify();
    }
}
