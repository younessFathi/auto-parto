package auto.parto.service;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.dto.PieceWithFilesDto;
import auto.parto.mapper.PieceDbMapper;
import auto.parto.model.Piece;
import auto.parto.notification.Notification;
import auto.parto.repository.ISearchPieceRepository;
import auto.parto.service.iservice.IFileService;
import auto.parto.validator.ValidCustomerId;
import auto.parto.validator.ValidPieceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SearchPieceServiceTest {
    @Mock
    private ISearchPieceRepository searchPieceRepository;
    @Mock
    private Notification notification;
    @Mock
    private ValidCustomerId validCustomerId;
    @Mock
    private ValidPieceId validPieceId;
    @Mock
    private PieceOfferService pieceOfferService;
    @Mock
    private IFileService fileService;
    @Mock
    private PieceDbMapper pieceDbMapper;

    @InjectMocks
    private SearchPieceService searchPieceService;

    @Test
    void testNotifySellers() {
        PieceDto pieceDto = new PieceDto();
        when(validCustomerId.isValid(pieceDto)).thenReturn(Mono.just(pieceDto));
        when(notification.notifySellers(pieceDto)).thenReturn(Mono.empty());

        StepVerifier.create(searchPieceService.notifySellers(pieceDto))
                .expectNext(pieceDto)
                .verifyComplete();

        verify(validCustomerId, times(1)).isValid(pieceDto);
        verify(notification, times(1)).notifySellers(pieceDto);
    }

    @Test
    void testGetPieceWithImagesByPieceId() {
        String pieceId = "test-id";
        Piece piece = new Piece();
        PieceWithFilesDto pieceWithFilesDto = new PieceWithFilesDto();
        when(searchPieceRepository.getPieceById(pieceId)).thenReturn(Mono.just(piece));
        when(pieceDbMapper.toDto(piece, null)).thenReturn(pieceWithFilesDto);

        StepVerifier.create(searchPieceService.getPieceWithImagesByPieceId(pieceId))
                .expectNext(pieceWithFilesDto)
                .verifyComplete();

        verify(searchPieceRepository, times(1)).getPieceById(pieceId);
        verify(pieceDbMapper, times(1)).toDto(piece, null);
    }

    @Test
    void testSavePieceWithImages() {
        PieceDto pieceDto = new PieceDto();
        Piece piece = new Piece();
        when(pieceDbMapper.toEntity(pieceDto)).thenReturn(piece);
        when(searchPieceRepository.saveSearchPiece(piece)).thenReturn(Mono.just(piece));
        when(pieceDbMapper.toDto(piece)).thenReturn(pieceDto);

        StepVerifier.create(searchPieceService.savePieceWithImages(pieceDto))
                .expectNext(pieceDto)
                .verifyComplete();

        verify(pieceDbMapper, times(1)).toEntity(pieceDto);
        verify(searchPieceRepository, times(1)).saveSearchPiece(piece);
        verify(pieceDbMapper, times(1)).toDto(piece);
    }

    @Test
    void testNotifyCustomer() {
        PieceOfferDto pieceOfferDto = new PieceOfferDto();
        when(validPieceId.isValid(pieceOfferDto)).thenReturn(Mono.just(pieceOfferDto));
        when(validCustomerId.isValid(pieceOfferDto)).thenReturn(Mono.just(pieceOfferDto));
        when(notification.notifyCustomer(pieceOfferDto)).thenReturn(Mono.just(pieceOfferDto));

        StepVerifier.create(searchPieceService.notifyCustomer(pieceOfferDto))
                .expectNext(pieceOfferDto)
                .verifyComplete();

        verify(validPieceId, times(1)).isValid(pieceOfferDto);
        verify(validCustomerId, times(1)).isValid(pieceOfferDto);
        verify(notification, times(1)).notifyCustomer(pieceOfferDto);
    }

    @Test
    void testCreateOffer() {
        PieceOfferDto pieceOfferDto = new PieceOfferDto();
        when(pieceOfferService.save(pieceOfferDto)).thenReturn(Mono.just(pieceOfferDto));

        StepVerifier.create(searchPieceService.createOffer(pieceOfferDto))
                .expectNext(pieceOfferDto)
                .verifyComplete();

        verify(pieceOfferService, times(1)).save(pieceOfferDto);
    }

    @Test
    void testCheckIfPieceById() {
        String pieceId = "test-id";
        when(searchPieceRepository.getPieceById(pieceId)).thenReturn(Mono.just(new Piece()));

        StepVerifier.create(searchPieceService.checkIfPieceById(pieceId))
                .expectNext(true)
                .verifyComplete();

        verify(searchPieceRepository, times(1)).getPieceById(pieceId);
    }
}
