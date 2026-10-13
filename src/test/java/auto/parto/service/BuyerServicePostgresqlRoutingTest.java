package auto.parto.service;

import auto.parto.mapper.BuyerDbMapper;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.mapper.BuyerIdentityMapper;
import auto.parto.dto.BuyerDto;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.persistence.BuyerProfileRepository;
import auto.parto.persistence.BuyerIdentityOtpRepository;
import auto.parto.persistence.BuyerIdentityRecord;
import auto.parto.repository.ISellerRepository;
import auto.parto.repository.mongo_repository.BuyerRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private final BuyerIdentityOtpRepository otpRepository = mock(BuyerIdentityOtpRepository.class);
    private final BuyerIdentityMapper identityMapper = mock(BuyerIdentityMapper.class);
    private final BuyerService service = new BuyerService(mongoRepository, sellerRepository, buyerMapper,
            sellerMapper, users, profiles, otpRepository, identityMapper);

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

    @Test
    void buyerPhoneAndOtpMethodsUseCanonicalIdentityRepository() {
        BuyerIdentityRecord identity = new BuyerIdentityRecord(BUYER_ID, "buyer@example.test",
                "Development", "Buyer", "+212600000101", true, "hash", Instant.now().plusSeconds(300), 0, null);
        BuyerDto dto = BuyerDto.builder().id(BUYER_ID.toString()).email(identity.email()).build();
        when(otpRepository.findByPhoneNumber(identity.phoneNumber())).thenReturn(Mono.just(identity));
        when(otpRepository.replaceOtp(eq(identity.email()), eq("123456"), any(Instant.class)))
                .thenReturn(Mono.just(identity));
        when(otpRepository.recordFailedAttempt(eq(identity.email()), any(Instant.class))).thenReturn(Mono.empty());
        when(otpRepository.findByEmail(identity.email())).thenReturn(Mono.just(identity));
        when(identityMapper.toDto(identity)).thenReturn(dto);

        assertThat(service.checkIfBuyerExistUsingPhoneNumber(identity.phoneNumber()).block()).isTrue();
        assertThat(service.findBuyerByPhoneNumber(identity.phoneNumber()).block()).isSameAs(dto);
        assertThat(service.updateGeneratedOtpByPhoneNumber(identity.phoneNumber(), "123456").block()).isSameAs(dto);
        assertThat(service.recordFailedOtpAttemptByEmail(identity.email()).block()).isSameAs(dto);
        verify(mongoRepository, never()).findBy(any(), any());
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
