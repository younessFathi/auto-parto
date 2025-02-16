package auto.parto.api;

import auto.parto.dto.FileDto;
import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.dto.PieceWithFilesDto;
import auto.parto.enums.*;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.IPieceOfferService;
import auto.parto.service.iservice.IPieceService;
import auto.parto.service.iservice.ISearchPieceService;
import auto.parto.utils.JsonUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

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
    private IPieceService pieceService;

    @MockitoBean
    private IPieceOfferService pieceOfferService;

    @MockitoBean
    private JsonUtil jsonUtil;

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
        PieceWithFilesDto pieceWithFilesDto = PieceWithFilesDto.builder().build();
        Mockito.when(searchPieceService.getPieceWithImagesByPieceId(anyString())).thenReturn(Mono.just(pieceWithFilesDto));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get()
                .uri("/search/piece/12345")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PieceWithFilesDto.class)
                .isEqualTo(pieceWithFilesDto);
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
        PieceOfferDto pieceOfferDto = PieceOfferDto.builder()
                .id(UUID.randomUUID().toString())
                .pieceId("7e9de0bf-e0d2-48ab-8f5b-f09aa6346eee")
                .customerId("f354855f-c3a0-4520-9239-fe18096db10a")
                .name("Brake Pad")
                .description("This is a sample piece offer description.")
                .price(new BigDecimal(0))
                .currency(Currency.USD)
                .condition(Condition.NEW)
                .isNegotiable(true)
                .isAvailable(true)
                .status(Status.PENDING)
                .seeNumber(10)
                .deliveryMode(DeliveryMode.EXPRESS)
                .isFreeDelivery(false)
                .deliveryPrice(new BigDecimal(11))
                .build();

        // Mock FileDto (if files are processed)
        FileDto fileDto = new FileDto();
        fileDto.setId("file123");
        FilePart mockFilePart = Mockito.mock(FilePart.class);

        // Mock behavior
        Mockito.when(jsonUtil.fromJson(anyString() , any()))
                .thenReturn(Mono.just(pieceOfferDto));
        Mockito.when(searchPieceService.notifyCustomer(any())).thenReturn(Mono.just(pieceOfferDto));
        Mockito.when(pieceOfferService.create(any())).thenReturn(Mono.just(pieceOfferDto));
        Mockito.when(fileManagerService.save(any(), anyString())).thenReturn(Mono.just(""));
        Mockito.when(pieceOfferService.update(any())).thenReturn(Mono.just(pieceOfferDto));

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
                 "id": "12345",
                 "name": "Sample Piece",
                 "description": "This is a sample piece description.",
                 "price": 100.50,
                 "currency": "USD",
                 "condition": "NEW",
                 "category": "CAR",
                 "isAvailable": true,
                 "status": "PENDING",
                 "seeNumber": 10,
                 "deliveryMode": "STANDARD",
                 "isFreeDelivery": false,
                 "deliveryPrice": 15.00,
                 "warranty": 12,
                 "duration": 24
                }
                """;

        // Mock piece DTO
        PieceDto pieceDto = PieceDto.builder()
                .id(UUID.randomUUID().toString())
                .name("Brake Pad")
                .description("This is a sample piece description.")
                .condition(Condition.NEW)
                .category(Category.BICYCLE)
                .seeNumber(10)
                .build();

        // Mock FileDto (if files are processed)
        FileDto fileDto = new FileDto();
        fileDto.setId("file123");

        // Mock service behaviors
        Mockito.when(jsonUtil.fromJson(anyString(), any()))
                .thenReturn(Mono.just(pieceDto));

        Mockito.when(searchPieceService.notifySellers(any(PieceDto.class)))
                .thenReturn(Mono.just(pieceDto));

        Mockito.when(fileManagerService.save(any() , anyString()))
                .thenReturn(Mono.just(""));

        Mockito.when(pieceService.create(any()))
                .thenReturn(Mono.just(pieceDto));

        Mockito.when(pieceService.update(any()))
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
