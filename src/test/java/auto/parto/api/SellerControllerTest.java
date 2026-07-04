package auto.parto.api;

import auto.parto.dto.SellerDto;
import auto.parto.enums.CarPartType;
import auto.parto.service.iservice.ISellerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = SellerController.class)
@Import(TestConfig.class)
class SellerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ISellerService sellerService;

    @Test
    @WithMockUser
    void createSeller_returns200() {
        SellerDto input = SellerDto.builder()
                .name("Test Seller")
                .email("seller@test.com")
                .phoneNumber("+212600000000")
                .carPartType(CarPartType.NEW)
                .build();
        SellerDto output = SellerDto.builder()
                .id("seller-1")
                .name("Test Seller")
                .email("seller@test.com")
                .phoneNumber("+212600000000")
                .carPartType(CarPartType.NEW)
                .build();
        when(sellerService.create(any(SellerDto.class))).thenReturn(Mono.just(output));

        webTestClient.post()
                .uri("/seller/create")
                .bodyValue(input)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("seller-1")
                .jsonPath("$.name").isEqualTo("Test Seller");
    }

    @Test
    @WithMockUser
    void getSellerById_returns200() {
        SellerDto dto = SellerDto.builder().id("seller-1").name("Test Seller").build();
        when(sellerService.getSellerById("seller-1")).thenReturn(Mono.just(dto));

        webTestClient.get()
                .uri("/seller/seller-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("seller-1");
    }

    @Test
    @WithMockUser
    void getSellerById_returns404_whenNotFound() {
        when(sellerService.getSellerById("unknown")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/seller/unknown")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMockUser
    void addFunds_returns200() {
        SellerDto dto = SellerDto.builder().id("seller-1").build();
        when(sellerService.addFunds("seller-1", new BigDecimal("100.00"))).thenReturn(Mono.just(dto));

        webTestClient.post()
                .uri("/seller/seller-1/wallet/add?amount=100.00")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser
    void deductFunds_returns200() {
        SellerDto dto = SellerDto.builder().id("seller-1").build();
        when(sellerService.addFunds("seller-1", new BigDecimal("-50.00"))).thenReturn(Mono.just(dto));

        webTestClient.post()
                .uri("/seller/seller-1/wallet/deduct?amount=50.00")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser
    void deactivateSeller_returns200() {
        SellerDto dto = SellerDto.builder().id("seller-1").isActive(false).build();
        when(sellerService.deactivate("seller-1")).thenReturn(Mono.just(dto));

        webTestClient.post()
                .uri("/seller/seller-1/deactivate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.isActive").isEqualTo(false);
    }

    @Test
    void deactivateSeller_returns401_whenNotAuthenticated() {
        webTestClient.post()
                .uri("/seller/seller-1/deactivate")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
