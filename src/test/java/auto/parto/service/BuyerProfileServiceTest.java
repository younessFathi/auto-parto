package auto.parto.service;

import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.persistence.BuyerProfileRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyerProfileServiceTest {

    @Test
    void updatesUsingCanonicalPublicId() {
        BuyerProfileRepository repository = mock(BuyerProfileRepository.class);
        BuyerProfileService service = new BuyerProfileService(repository);
        UUID userPublicId = UUID.randomUUID();
        BuyerProfileRecord profile = new BuyerProfileRecord(UUID.randomUUID(), userPublicId,
                "Ada", "Lovelace", "+212600000000", "https://example.test/avatar");
        when(repository.updateByUserPublicId(userPublicId, "Ada", "Lovelace", "+212600000000",
                "https://example.test/avatar")).thenReturn(Mono.just(profile));

        StepVerifier.create(service.update(userPublicId, "Ada", "Lovelace", "+212600000000",
                                "https://example.test/avatar"))
                .assertNext(result -> assertThat(result).isEqualTo(profile))
                .verifyComplete();

        verify(repository).updateByUserPublicId(userPublicId, "Ada", "Lovelace", "+212600000000",
                "https://example.test/avatar");
    }
}
