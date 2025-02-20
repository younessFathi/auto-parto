package auto.parto.api;


import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import auto.parto.service.iservice.IPieceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PieceController.class)
public class PieceControllerIT {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IPieceService pieceService;

    @Test
    @WithMockUser
    public void testGetAllPiecesByCustomerId_NoPiecesFound() {
        String customerId = "customer1";
        PagedResponse<PieceDto> response = new PagedResponse<>();
        response.setData(List.of());
        response.setPage(0);
        response.setSize(10);
        response.setTotalItems(0);

        when(pieceService.getAllPiecesByCustomerId(anyString() , anyInt() , anyInt())).thenReturn(Mono.just(response));
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get()
                .uri("/pieces/{customerId}?page=0&size=10", customerId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.data").isEmpty()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalItems").isEqualTo(0);
    }


    @Test
    @WithMockUser
    public void testGetAllPiecesByCustomerId_WithPieces() {
        String customerId = "customer1";
        PagedResponse<PieceDto> response = new PagedResponse<>();
        response.setData(List.of(PieceDto.builder().build()));
        response.setPage(0);
        response.setSize(10);
        response.setTotalItems(3);

        when(pieceService.getAllPiecesByCustomerId(anyString() , anyInt() , anyInt())).thenReturn(Mono.just(response));
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get()
                .uri("/pieces/{customerId}?page=0&size=10", customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isNotEmpty()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalItems").isEqualTo(3);
    }
}
