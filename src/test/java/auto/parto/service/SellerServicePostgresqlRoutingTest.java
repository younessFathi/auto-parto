package auto.parto.service;

import auto.parto.mapper.SellerDbMapper;
import auto.parto.mapper.SellerIdentityMapper;
import auto.parto.dto.SellerDto;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.SellerProfileRecord;
import auto.parto.persistence.SellerProfileRepository;
import auto.parto.persistence.WalletMutationResult;
import auto.parto.persistence.WalletRecord;
import auto.parto.persistence.WalletRepository;
import auto.parto.persistence.SellerIdentityOtpRepository;
import auto.parto.persistence.SellerIdentityRecord;
import auto.parto.repository.ISellerRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerServicePostgresqlRoutingTest {

    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    private final ISellerRepository mongoRepository = mock(ISellerRepository.class);
    private final SellerDbMapper mapper = mock(SellerDbMapper.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final SellerProfileRepository profiles = mock(SellerProfileRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final SellerIdentityOtpRepository otpRepository = mock(SellerIdentityOtpRepository.class);
    private final SellerIdentityMapper identityMapper = mock(SellerIdentityMapper.class);
    private final SellerService service = new SellerService(mongoRepository, mapper, users, profiles, wallets,
            otpRepository, identityMapper);

    @Test
    void addFundsUsesMinorUnitsAndPostgresqlWallet() {
        AppUserRecord user = user();
        SellerProfileRecord profile = profile();
        WalletRecord wallet = new WalletRecord(UUID.randomUUID(), SELLER_ID, 1234L, "MAD", 1L);
        when(wallets.mutate(SELLER_ID, "MAD", 1234L, "CREDIT", "wallet-test-1", "seller-wallet-api"))
                .thenReturn(Mono.just(new WalletMutationResult(wallet, UUID.randomUUID(), true)));
        when(users.findByPublicId(SELLER_ID)).thenReturn(Mono.just(user));
        when(profiles.findByUserPublicId(SELLER_ID)).thenReturn(Mono.just(profile));

        var result = service.addFunds(SELLER_ID.toString(), new BigDecimal("12.34"),
                auto.parto.enums.Currency.MAD, "wallet-test-1").block();

        assertThat(result.getId()).isEqualTo(SELLER_ID.toString());
        assertThat(result.getWalletDto().getBalanceMinor()).isEqualTo(1234L);
        assertThat(result.getWalletDto().getCurrency()).isEqualTo(auto.parto.enums.Currency.MAD);
        verify(mongoRepository, never()).addFunds(any(), anyLong());
    }

    @Test
    void sellerReadUsesCanonicalPostgresqlIdentityAndProfile() {
        when(users.findByPublicId(SELLER_ID)).thenReturn(Mono.just(user()));
        when(profiles.findByUserPublicId(SELLER_ID)).thenReturn(Mono.just(profile()));

        var result = service.getSellerById(SELLER_ID.toString()).block();

        assertThat(result.getEmail()).isEqualTo("seller@example.test");
        assertThat(result.getName()).isEqualTo("Development Seller");
        verify(mongoRepository, never()).getSellerById(any());
    }

    @Test
    void sellerOtpMethodsUseCanonicalOtpRepository() {
        SellerIdentityRecord identity = new SellerIdentityRecord(SELLER_ID, "seller@example.test",
                "Development", "Seller", true, "hash", Instant.now().plusSeconds(300), 0, null);
        SellerDto dto = SellerDto.builder().id(SELLER_ID.toString()).email(identity.email()).build();
        when(otpRepository.replaceOtp(eq(identity.email()), eq("123456"), any(Instant.class)))
                .thenReturn(Mono.just(identity));
        when(otpRepository.recordFailedAttempt(eq(identity.email()), any(Instant.class))).thenReturn(Mono.empty());
        when(otpRepository.findByEmail(identity.email())).thenReturn(Mono.just(identity));
        when(identityMapper.toDto(identity)).thenReturn(dto);

        assertThat(service.updateGeneratedOtpByEmail(identity.email(), "123456").block()).isSameAs(dto);
        assertThat(service.recordFailedOtpAttempt(identity.email()).block()).isSameAs(dto);
        verify(mongoRepository, never()).updateGeneratedOtp(any(), any(), any());
    }

    private static AppUserRecord user() {
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        return new AppUserRecord(SELLER_ID, "OTP", null, "seller@example.test", "SELLER",
                "ACTIVE", "v1", now, now);
    }

    private static SellerProfileRecord profile() {
        return new SellerProfileRecord(UUID.randomUUID(), SELLER_ID, "Development", "Seller",
                "+212600000102", "BOTH", "description");
    }
}
