package auto.parto.service;

import auto.parto.dto.SellerDto;
import auto.parto.dto.WalletDto;
import auto.parto.exception.InsufficientBalanceException;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.model.Seller;
import auto.parto.model.Wallet;
import auto.parto.repository.ISellerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerServiceAddFundsTest {

    @Mock
    private ISellerRepository sellerRepository;
    @Mock
    private SellerDbMapper sellerDbMapper;

    @InjectMocks
    private SellerService sellerService;

    private SellerDto createSellerDto(BigDecimal balance) {
        return SellerDto.builder()
                .id("seller-1")
                .walletDto(WalletDto.builder().balance(balance).build())
                .build();
    }

    @Test
    void addFunds_negativeAmount_throwsInsufficientBalance_whenResultIsNegative() {
        when(sellerRepository.addFunds("seller-1", new BigDecimal("-100.00")))
                .thenReturn(Mono.empty());

        StepVerifier.create(sellerService.addFunds("seller-1", new BigDecimal("-100.00")))
                .expectError(InsufficientBalanceException.class)
                .verify();
    }

    @Test
    void addFunds_negativeAmount_succeeds_whenResultIsZero() {
        Seller seller = Seller.builder()
                .id("seller-1")
                .wallet(Wallet.builder().balance(BigDecimal.ZERO).build())
                .build();

        when(sellerRepository.addFunds(eq("seller-1"), any(BigDecimal.class)))
                .thenReturn(Mono.just(seller));
        when(sellerDbMapper.toDto(any(Seller.class))).thenReturn(createSellerDto(BigDecimal.ZERO));

        StepVerifier.create(sellerService.addFunds("seller-1", new BigDecimal("-100.00")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void addFunds_negativeAmount_succeeds_whenResultIsPositive() {
        Seller seller = Seller.builder()
                .id("seller-1")
                .wallet(Wallet.builder().balance(new BigDecimal("50.00")).build())
                .build();

        when(sellerRepository.addFunds(eq("seller-1"), any(BigDecimal.class)))
                .thenReturn(Mono.just(seller));
        when(sellerDbMapper.toDto(any(Seller.class))).thenReturn(createSellerDto(new BigDecimal("50.00")));

        StepVerifier.create(sellerService.addFunds("seller-1", new BigDecimal("-50.00")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void addFunds_positiveAmount_succeeds() {
        Seller seller = Seller.builder()
                .id("seller-1")
                .wallet(Wallet.builder().balance(new BigDecimal("150.00")).build())
                .build();

        when(sellerRepository.addFunds(eq("seller-1"), any(BigDecimal.class)))
                .thenReturn(Mono.just(seller));
        when(sellerDbMapper.toDto(any(Seller.class))).thenReturn(createSellerDto(new BigDecimal("150.00")));

        StepVerifier.create(sellerService.addFunds("seller-1", new BigDecimal("50.00")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void addFunds_zeroAmount_succeeds() {
        Seller seller = Seller.builder()
                .id("seller-1")
                .wallet(Wallet.builder().balance(new BigDecimal("100.00")).build())
                .build();

        when(sellerRepository.addFunds(eq("seller-1"), any(BigDecimal.class)))
                .thenReturn(Mono.just(seller));
        when(sellerDbMapper.toDto(any(Seller.class))).thenReturn(createSellerDto(new BigDecimal("100.00")));

        StepVerifier.create(sellerService.addFunds("seller-1", BigDecimal.ZERO))
                .expectNextCount(1)
                .verifyComplete();
    }
}