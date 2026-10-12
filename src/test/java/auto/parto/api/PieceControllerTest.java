package auto.parto.api;

import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import auto.parto.service.iservice.IPieceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

@WebFluxTest(controllers = PieceController.class)
@Import(TestConfig.class)
class PieceControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IPieceService pieceService;

    @Test
    void getAllPiecesByCustomerId_returns200() {
        PagedResponse<PieceDto> response = new PagedResponse<>();
        response.setData(List.of(PieceDto.builder().id("piece-1").name("Test Piece").build()));
        response.setPage(0);
        response.setSize(10);
        response.setTotalItems(1);

        when(pieceService.getAllPiecesByCustomerId(anyString(), anyInt(), anyInt())).thenReturn(Mono.just(response));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("customer1")))
                .get()
                .uri("/pieces/customer1?page=0&size=10")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getAllPiecesByCustomerId_returns404_whenEmpty() {
        PagedResponse<PieceDto> response = new PagedResponse<>();
        response.setData(List.of());
        response.setPage(0);
        response.setSize(10);
        response.setTotalItems(0);

        when(pieceService.getAllPiecesByCustomerId(anyString(), anyInt(), anyInt())).thenReturn(Mono.just(response));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("customer1")))
                .get()
                .uri("/pieces/customer1?page=0&size=10")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMockUser
    void getPieceDetailByPieceId_returns200() {
        PieceDto dto = PieceDto.builder().id("piece-1").name("Test Piece").build();
        when(pieceService.getPieceDetailByPieceId("piece-1")).thenReturn(Mono.just(dto));

        webTestClient.get()
                .uri("/pieces/piece-1/detail")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("piece-1")
                .jsonPath("$.name").isEqualTo("Test Piece");
    }

    @Test
    void getAllPiecesByCustomerId_returns401_whenNotAuthenticated() {
        webTestClient.get()
                .uri("/pieces/customer1?page=0&size=10")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getAllPiecesByCustomerId_returns403_forAnotherCustomer() {
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("customer1")))
                .get()
                .uri("/pieces/customer2?page=0&size=10")
                .exchange()
                .expectStatus().isForbidden();
    }
}
