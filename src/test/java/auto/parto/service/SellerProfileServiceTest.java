package auto.parto.service;

import auto.parto.persistence.SellerProfileRecord;
import auto.parto.persistence.SellerProfileRepository;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerProfileServiceTest {

    @Test
    void updatesUsingCanonicalPublicId() {
        SellerProfileRepository repository = mock(SellerProfileRepository.class);
        SellerProfileService service = new SellerProfileService(repository);
        UUID userPublicId = UUID.randomUUID();
        SellerProfileRecord profile = new SellerProfileRecord(UUID.randomUUID(), userPublicId,
                "Grace", "Hopper", "+212600000000", "ENGINE", "Computing");
        when(repository.updateByUserPublicId(userPublicId, "Grace", "Hopper", "+212600000000",
                "ENGINE", "Computing")).thenReturn(reactor.core.publisher.Mono.just(profile));

        StepVerifier.create(service.update(userPublicId, "Grace", "Hopper", "+212600000000",
                                "ENGINE", "Computing"))
                .assertNext(result -> assertThat(result).isEqualTo(profile))
                .verifyComplete();

        verify(repository).updateByUserPublicId(userPublicId, "Grace", "Hopper", "+212600000000",
                "ENGINE", "Computing");
    }
}
