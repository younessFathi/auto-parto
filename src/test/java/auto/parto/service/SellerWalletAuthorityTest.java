package auto.parto.service;

import auto.parto.enums.Currency;
import auto.parto.mapper.SellerIdentityMapper;
import auto.parto.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;

class SellerWalletAuthorityTest {
    @Test
    void sellerServiceCannotCreditEvenWithValidOwnerCurrencyAndKey() {
        WalletRepository wallets = mock(WalletRepository.class);
        SellerService service = new SellerService(mock(AppUserRepository.class), mock(SellerProfileRepository.class),
                wallets, mock(SellerIdentityOtpRepository.class), mock(SellerIdentityMapper.class), 5);
        StepVerifier.create(service.addFunds(UUID.randomUUID().toString(), new BigDecimal("10.00"), Currency.MAD, "key"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException status
                        && status.getStatusCode().value() == 403).verify();
        verifyNoInteractions(wallets);
    }
}
