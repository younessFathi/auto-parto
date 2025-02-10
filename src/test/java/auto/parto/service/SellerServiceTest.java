package auto.parto.service;

import auto.parto.dto.SellerDto;
import auto.parto.mapper.SellerMapper;
import auto.parto.model.Seller;
import auto.parto.repository.ISellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class SellerServiceTest {

    @Mock
    private ISellerRepository sellerRepository;

    @InjectMocks
    private SellerService sellerService;

    private SellerDto sellerDto;
    private Seller seller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        sellerDto = SellerDto.builder()
                .id("1")
                .email("test@example.com")
                .phoneNumber("1234567890")
                .name("Test Seller")
                .description("Test Description")
                .build();

        seller = SellerMapper.toEntity(sellerDto);
    }

    @Test
    void createSeller_Success() {
        when(sellerRepository.save(any(Seller.class))).thenReturn(Mono.just(seller));

        Mono<SellerDto> result = sellerService.create(sellerDto);

        StepVerifier.create(result)
                .expectNextMatches(savedSellerDto -> savedSellerDto.getEmail().equals(sellerDto.getEmail()) &&
                        savedSellerDto.getPhoneNumber().equals(sellerDto.getPhoneNumber()) &&
                        savedSellerDto.getName().equals(sellerDto.getName()) &&
                        savedSellerDto.getDescription().equals(sellerDto.getDescription()))
                .verifyComplete();
    }


    @Test
    void getSellerById_Success() {
        when(sellerRepository.getSellerById(anyString())).thenReturn(Mono.just(seller));

        Mono<SellerDto> result = sellerService.getSellerById("1");

        StepVerifier.create(result)
                .expectNextMatches(foundSellerDto -> foundSellerDto.getEmail().equals(sellerDto.getEmail()) &&
                        foundSellerDto.getPhoneNumber().equals(sellerDto.getPhoneNumber()) &&
                        foundSellerDto.getName().equals(sellerDto.getName()) &&
                        foundSellerDto.getDescription().equals(sellerDto.getDescription()))
                .verifyComplete();
    }

    @Test
    void getSellerById_NotFound() {
        when(sellerRepository.getSellerById(anyString())).thenReturn(Mono.empty());

        Mono<SellerDto> result = sellerService.getSellerById("2");

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();
    }
}
