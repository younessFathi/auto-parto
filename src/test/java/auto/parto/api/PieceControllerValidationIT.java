package auto.parto.api;

import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import auto.parto.service.iservice.IPieceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PieceController.class)
class PieceControllerValidationIT {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IPieceService pieceService;

    @Test
    @WithMockUser
    void rejectsNegativePage() {
        webTestClient.get()
                .uri("/pieces/customer1?page=-1&size=10")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.message").isEqualTo("page must be >= 0")
                .jsonPath("$.path").isEqualTo("/pieces/customer1");
    }

    @Test
    @WithMockUser
    void rejectsOversizedPageSize() {
        webTestClient.get()
                .uri("/pieces/customer1?page=0&size=101")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.message").isEqualTo("size must be between 1 and 100")
                .jsonPath("$.path").isEqualTo("/pieces/customer1");
    }

    @Test
    @WithMockUser
    void acceptsValidPageAndSize() {
        PagedResponse<PieceDto> response = new PagedResponse<>();
        response.setData(List.of(PieceDto.builder().build()));
        response.setPage(0);
        response.setSize(10);
        response.setTotalItems(1);
        when(pieceService.getAllPiecesByCustomerId(anyString(), anyInt(), anyInt())).thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/pieces/customer1?page=0&size=10")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser
    void returnsNotFoundWhenPieceDetailDoesNotExist() {
        when(pieceService.getPieceDetailByPieceId(anyString())).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/pieces/piece-404/detail")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.message").isEqualTo("Piece not found with id: piece-404")
                .jsonPath("$.path").isEqualTo("/pieces/piece-404/detail");
    }
}
