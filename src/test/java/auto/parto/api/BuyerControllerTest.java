package auto.parto.api;

import auto.parto.dto.BuyerDto;
import auto.parto.service.iservice.IBuyerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = BuyerController.class)
@Import(TestConfig.class)
class BuyerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IBuyerService buyerService;

    @Test
    @WithMockUser
    void deactivateBuyer_returns200() {
        BuyerDto dto = BuyerDto.builder().id("id-1").isActive(false).build();
        when(buyerService.deactivateBuyer("id-1")).thenReturn(Mono.just(dto));

        webTestClient.put()
                .uri("/customers/id-1/deactivate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("id-1")
                .jsonPath("$.isActive").isEqualTo(false);
    }

    @Test
    void deactivateBuyer_returns401_whenNotAuthenticated() {
        webTestClient.put()
                .uri("/customers/id-1/deactivate")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
