package auto.parto.service;

import auto.parto.mapper.BuyerDbMapper;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.persistence.BuyerProfileRepository;
import auto.parto.repository.ISellerRepository;
import auto.parto.repository.mongo_repository.BuyerRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyerServicePostgresqlRoutingTest {

    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private final BuyerRepository mongoRepository = mock(BuyerRepository.class);
    private final ISellerRepository sellerRepository = mock(ISellerRepository.class);
    private final BuyerDbMapper buyerMapper = mock(BuyerDbMapper.class);
    private final SellerDbMapper sellerMapper = mock(SellerDbMapper.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final BuyerProfileRepository profiles = mock(BuyerProfileRepository.class);
    private final BuyerService service = new BuyerService(mongoRepository, sellerRepository, buyerMapper,
            sellerMapper, users, profiles);

    @Test
    void buyerExistenceUsesActivePostgresqlIdentity() {
        when(users.findByPublicId(BUYER_ID)).thenReturn(Mono.just(user("ACTIVE", "BUYER")));

        assertThat(service.checkIfBuyerExistUsingId(BUYER_ID.toString()).block()).isTrue();
        verify(mongoRepository, never()).existsById(any());
    }

    @Test
    void buyerReadAndDeactivationUsePostgresqlIdentityAndProfile() {
        when(users.findByEmail("buyer@example.test")).thenReturn(Mono.just(user("ACTIVE", "BUYER")));
        when(users.updateStatus(BUYER_ID, "INACTIVE")).thenReturn(Mono.just(user("INACTIVE", "BUYER")));
        when(profiles.findByUserPublicId(BUYER_ID)).thenReturn(Mono.just(profile()));

        var read = service.findBuyerByEmail("buyer@example.test").block();
        var deactivated = service.deactivateBuyer(BUYER_ID.toString()).block();

        assertThat(read.getId()).isEqualTo(BUYER_ID.toString());
        assertThat(read.getFirstName()).isEqualTo("Development");
        assertThat(deactivated.getIsActive()).isFalse();
        verify(mongoRepository, never()).findBy(any(), any());
        verify(mongoRepository, never()).deactivateCustomer(any());
    }

    private static AppUserRecord user(String status, String role) {
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        return new AppUserRecord(BUYER_ID, "OTP", null, "buyer@example.test", role,
                status, "v1", now, now);
    }

    private static BuyerProfileRecord profile() {
        return new BuyerProfileRecord(UUID.randomUUID(), BUYER_ID, "Development", "Buyer",
                "+212600000101", "https://example.test/buyer.png");
    }
}
