package auto.parto.service;

import auto.parto.dto.PieceOfferDto;
import auto.parto.mapper.PieceOfferDbMapper;
import auto.parto.model.PieceOffer;
import auto.parto.repository.mongo_repository.PieceOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PieceOfferServiceTest {
    @Mock
    private PieceOfferRepository pieceOfferRepository;
    @Mock
    private PieceOfferDbMapper pieceOfferDbMapper;

    @InjectMocks
    private PieceOfferService pieceOfferService;

    private PieceOfferDto pieceOfferDto;
    private PieceOffer pieceOffer;

    @BeforeEach
    void setUp() {
        pieceOfferDto = PieceOfferDto.builder()
                .id("test-id")
                .pieceId("piece-id")
                .customerId("customer-id")
                .name("Test Offer")
                .build();

        pieceOffer = PieceOffer.builder()
                .id("test-id")
                .pieceId("piece-id")
                .customerId("customer-id")
                .name("Test Offer")
                .build();
    }

    @Test
    void testSave() {
        when(pieceOfferDbMapper.toEntity(pieceOfferDto)).thenReturn(pieceOffer);
        when(pieceOfferRepository.save(pieceOffer)).thenReturn(Mono.just(pieceOffer));
        when(pieceOfferDbMapper.toDto(pieceOffer)).thenReturn(pieceOfferDto);

        StepVerifier.create(pieceOfferService.save(pieceOfferDto))
                .expectNext(pieceOfferDto)
                .verifyComplete();

        verify(pieceOfferDbMapper, times(1)).toEntity(pieceOfferDto);
        verify(pieceOfferRepository, times(1)).save(pieceOffer);
        verify(pieceOfferDbMapper, times(1)).toDto(pieceOffer);
    }

    @Test
    void testCreate() {
        when(pieceOfferDbMapper.toEntity(pieceOfferDto)).thenReturn(pieceOffer);
        when(pieceOfferRepository.save(pieceOffer)).thenReturn(Mono.just(pieceOffer));
        when(pieceOfferDbMapper.toDto(pieceOffer)).thenReturn(pieceOfferDto);

        StepVerifier.create(pieceOfferService.create(pieceOfferDto))
                .expectNext(pieceOfferDto)
                .verifyComplete();

        verify(pieceOfferDbMapper, times(1)).toEntity(pieceOfferDto);
        verify(pieceOfferRepository, times(1)).save(pieceOffer);
        verify(pieceOfferDbMapper, times(1)).toDto(pieceOffer);
    }

    @Test
    void testUpdate() {
        when(pieceOfferRepository.update(pieceOfferDto.getId(), pieceOfferDto.getImagesId()))
                .thenReturn(Mono.just(pieceOffer));
        when(pieceOfferDbMapper.toDto(pieceOffer)).thenReturn(pieceOfferDto);

        StepVerifier.create(pieceOfferService.update(pieceOfferDto))
                .expectNext(pieceOfferDto)
                .verifyComplete();

        verify(pieceOfferRepository, times(1)).update(pieceOfferDto.getId(), pieceOfferDto.getImagesId());
        verify(pieceOfferDbMapper, times(1)).toDto(pieceOffer);
    }

    @Test
    void testUpdateWithError() {
        when(pieceOfferRepository.update(pieceOfferDto.getId(), pieceOfferDto.getImagesId()))
                .thenReturn(Mono.error(new RuntimeException("Update failed")));

        StepVerifier.create(pieceOfferService.update(pieceOfferDto))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && throwable.getMessage().equals("Update failed"))
                .verify();

        verify(pieceOfferRepository, times(1)).update(pieceOfferDto.getId(), pieceOfferDto.getImagesId());
        verifyNoInteractions(pieceOfferDbMapper);
    }
}
