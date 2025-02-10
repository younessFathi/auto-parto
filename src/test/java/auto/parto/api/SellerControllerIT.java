package auto.parto.api;

import auto.parto.dto.SellerDto;
import auto.parto.dto.WalletDto;
import auto.parto.enums.CarPartType;
import auto.parto.service.iservice.ISellerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@WebFluxTest(SellerController.class)
public class SellerControllerIT {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ISellerService sellerService;

    private SellerDto sellerDto;

    @BeforeEach
    void setUp() {
        sellerDto = SellerDto.builder()
                .id("1")
                .email("test@example.com")
                .phoneNumber("1234567890")
                .name("Test Seller")
                .description("Test Description")
                .carPartType(CarPartType.BOTH)
                .walletDto(WalletDto.builder()
                        .id("1")
                        .balance(new BigDecimal("100.00"))
                        .currency("USD")
                        .build())
                .build();
    }

    @Test
    @WithMockUser
    void createSeller() {
        Mockito.when(sellerService.create(Mockito.any(SellerDto.class)))
                .thenReturn(Mono.just(sellerDto));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/seller/create")
                .bodyValue(sellerDto)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SellerDto.class)
                .isEqualTo(sellerDto);
    }

    @Test
    @WithMockUser
    void createSeller_MissingName() {
        SellerDto invalidSellerDto = SellerDto.builder()
                .email("test@example.com")
                .phoneNumber("1234567890")
                .description("Test Description")
                .carPartType(CarPartType.BOTH)
                .walletDto(WalletDto.builder()
                        .id("1")
                        .balance(new BigDecimal("100.00"))
                        .currency("USD")
                        .build())
                .build();

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/seller/create")
                .bodyValue(invalidSellerDto)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser
    void createSeller_InvalidEmail() {
        SellerDto invalidSellerDto = SellerDto.builder()
                .name("Test Seller")
                .email("invalid-email")
                .phoneNumber("1234567890")
                .description("Test Description")
                .carPartType(CarPartType.BOTH)
                .walletDto(WalletDto.builder()
                        .id("1")
                        .balance(new BigDecimal("100.00"))
                        .currency("USD")
                        .build())
                .build();

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/seller/create")
                .bodyValue(invalidSellerDto)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser
    void createSeller_MissingPhoneNumber() {
        SellerDto invalidSellerDto = SellerDto.builder()
                .name("Test Seller")
                .email("test@example.com")
                .description("Test Description")
                .carPartType(CarPartType.BOTH)
                .walletDto(WalletDto.builder()
                        .id("1")
                        .balance(new BigDecimal("100.00"))
                        .currency("USD")
                        .build())
                .build();

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/seller/create")
                .bodyValue(invalidSellerDto)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser
    void createSeller_MissingCarPartType() {
        SellerDto invalidSellerDto = SellerDto.builder()
                .name("Test Seller")
                .email("test@example.com")
                .phoneNumber("1234567890")
                .description("Test Description")
                .walletDto(WalletDto.builder()
                        .id("1")
                        .balance(new BigDecimal("100.00"))
                        .currency("USD")
                        .build())
                .build();

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/seller/create")
                .bodyValue(invalidSellerDto)
                .exchange()
                .expectStatus().isBadRequest();
    }


    @Test
    @WithMockUser
    void getSellerById_NotFound() {
        Mockito.when(sellerService.getSellerById("2"))
                .thenReturn(Mono.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get()
                .uri("/seller/2")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Seller not found");
    }


}
