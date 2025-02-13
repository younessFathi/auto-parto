package auto.parto.api;

import auto.parto.dto.FileDto;
import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.ISearchPieceService;
import auto.parto.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.*;

@WebFluxTest(SearchController.class)
public class SearchControllerIT {
    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ISearchPieceService searchPieceService;

    @MockitoBean
    private IFileManagerService fileManagerService;

    @MockitoBean
    private JsonUtil<PieceDto> pieceDeserializer;

    @MockitoBean
    private JsonUtil<PieceOfferDto> pieceOfferDeserializer;

    private PieceDto pieceDto;
    private PieceOfferDto pieceOfferDto;

    @BeforeEach
    public void setUp() {
        pieceDto = new PieceDto();
        pieceDto.setId("12345");
        pieceDto.setName("Sample Piece");

        pieceOfferDto = new PieceOfferDto();
        pieceOfferDto.setId("54321");
        pieceOfferDto.setName("Sample Offer");
    }

    @Test
    @WithMockUser
    public void testGetPieceById() {
        Mockito.when(searchPieceService.getPieceWithImagesByPieceId(anyString())).thenReturn(Mono.just(pieceDto));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get()
                .uri("/search/piece/12345")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PieceDto.class)
                .isEqualTo(pieceDto);
    }

    @Test
    @WithMockUser
    public void testCreateOfferAndNotifyCustomer() {
        // Mock JSON string
        String pieceOfferJson = """
                {
                  "pieceId": "7e9de0bf-e0d2-48ab-8f5b-f09aa6346eee",
                  "customerId": "f354855f-c3a0-4520-9239-fe18096db10a",
                  "name": "Sample Piece Offer",
                  "description": "This is a sample piece offer description.",
                  "price": 100.50,
                  "currency": "USD",
                  "condition": "NEW",
                  "isNegotiable": true,
                  "isAvailable": true,
                  "status": "PENDING",
                  "seeNumber": 10,
                  "deliveryMode": "STANDARD",
                  "isFreeDelivery": false,
                  "deliveryPrice": 15.00
                }
                """;

        // Mock piece DTO
        PieceOfferDto pieceOfferDto = new PieceOfferDto();
        pieceOfferDto.setName("Brake Pad");
        pieceOfferDto.setCondition(Condition.NEW);

        // Mock FileDto (if files are processed)
        FileDto fileDto = new FileDto();
        fileDto.setId("file123");

        // Mock service behaviors
        Mockito.when(pieceOfferDeserializer.fromJson(eq(pieceOfferJson), eq(PieceOfferDto.class)))
                .thenReturn(Mono.just(pieceOfferDto));

        Mockito.when(searchPieceService.notifyCustomer(any(PieceOfferDto.class)))
                .thenReturn(Mono.just(pieceOfferDto));

        Mockito.when(fileManagerService.save(any()))
                .thenReturn(Mono.just(fileDto));

        Mockito.when(searchPieceService.createOffer(any()))
                .thenReturn(Mono.just(pieceOfferDto));

        // Prepare multipart form data
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("pieceOffer", pieceOfferJson);
        bodyBuilder.part("images", new ClassPathResource("images.jpeg"));  // Sample image file

        // Execute request
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/search/piece-offer")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Brake Pad")
                .jsonPath("$.condition").isEqualTo(Condition.NEW);
    }

    @Test
    @WithMockUser
    public void testSearchPieceAndNotifySellers() {
        // Mock JSON string
        String pieceJson = """
                {
                  "name": "Brake Pad",
                  "description": "High-performance brake pad for sports cars.",
                  "condition": "NEW",
                  "category": "CAR"
                }
                """;

        // Mock piece DTO
        PieceDto pieceDto = new PieceDto();
        pieceDto.setName("Brake Pad");
        pieceDto.setCategory(Category.BICYCLE);

        // Mock FileDto (if files are processed)
        FileDto fileDto = new FileDto();
        fileDto.setId("file123");

        // Mock service behaviors
        Mockito.when(pieceDeserializer.fromJson(eq(pieceJson), eq(PieceDto.class)))
                .thenReturn(Mono.just(pieceDto));

        Mockito.when(searchPieceService.notifySellers(any(PieceDto.class)))
                .thenReturn(Mono.just(pieceDto));

        Mockito.when(fileManagerService.save(any()))
                .thenReturn(Mono.just(fileDto));

        Mockito.when(searchPieceService.savePieceWithImages(any()))
                .thenReturn(Mono.just(pieceDto));

        // Prepare multipart form data
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("piece", pieceJson);
        bodyBuilder.part("images", new ClassPathResource("images.jpeg"));  // Sample image file

        // Execute request
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/search/piece")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Brake Pad")
                .jsonPath("$.category").isEqualTo(Category.BICYCLE);
    }
}
