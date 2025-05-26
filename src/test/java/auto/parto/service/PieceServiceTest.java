package auto.parto.service;

import auto.parto.dto.PieceDto;
import auto.parto.mapper.PieceDbMapper;
import auto.parto.model.Piece;
import auto.parto.repository.IPieceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class PieceServiceTest {
    @Mock
    private IPieceRepository pieceRepository;
    @Mock
    private PieceDbMapper pieceDbMapper;

    @InjectMocks
    private PieceService pieceService;

    @Test
    void testCheckIfPieceById() {
        String pieceId = "test-id";
        when(pieceRepository.checkIfPieceById(pieceId)).thenReturn(Mono.just(true));

        StepVerifier.create(pieceService.checkIfPieceById(pieceId))
                .expectNext(true)
                .verifyComplete();

        verify(pieceRepository, times(1)).checkIfPieceById(pieceId);
    }

    @Test
    void testCreate() {
        PieceDto pieceDto = new PieceDto();
        Piece piece = new Piece();
        when(pieceDbMapper.toEntity(pieceDto)).thenReturn(piece);
        when(pieceRepository.save(piece)).thenReturn(Mono.just(piece));
        when(pieceDbMapper.toDto(piece)).thenReturn(pieceDto);

        StepVerifier.create(pieceService.create(pieceDto))
                .expectNext(pieceDto)
                .verifyComplete();

        verify(pieceDbMapper, times(1)).toEntity(pieceDto);
        verify(pieceRepository, times(1)).save(piece);
        verify(pieceDbMapper, times(1)).toDto(piece);
    }

    @Test
    void testUpdate() {
        PieceDto pieceDto = new PieceDto();
        pieceDto.setId("test-id");
        when(pieceRepository.update(eq(pieceDto.getId()), any())).thenReturn(Mono.just(new Piece()));
        when(pieceDbMapper.toDto(any())).thenReturn(pieceDto);

        StepVerifier.create(pieceService.update(pieceDto))
                .expectNext(pieceDto)
                .verifyComplete();

        verify(pieceRepository, times(1)).update(eq(pieceDto.getId()), any());
        verify(pieceDbMapper, times(1)).toDto(any());
    }

    @Test
    void testGetAllPiecesByCustomerId() {
        String customerId = "customer-id";
        int page = 0;
        int size = 10;
        Piece piece = new Piece();
        PieceDto pieceDto = new PieceDto();
        when(pieceRepository.getAllPieces(customerId, page, size)).thenReturn(Mono.just(piece).flux());
        when(pieceDbMapper.toDto(piece)).thenReturn(pieceDto);
        when(pieceRepository.getTotalPieces()).thenReturn(Mono.just(1L));

        StepVerifier.create(pieceService.getAllPiecesByCustomerId(customerId, page, size))
                .expectNextMatches(response -> response.getData().equals(Collections.singletonList(pieceDto)) &&
                        response.getTotalItems() == 1L)
                .verifyComplete();

        verify(pieceRepository, times(1)).getAllPieces(customerId, page, size);
        verify(pieceRepository, times(1)).getTotalPieces();
        verify(pieceDbMapper, times(1)).toDto(piece);
    }

    @Test
    void testGetPieceDetailByPieceId() {
        String pieceId = "test-id";
        Piece piece = new Piece();
        PieceDto pieceDto = new PieceDto();
        when(pieceRepository.findById(pieceId)).thenReturn(Mono.just(piece));
        when(pieceDbMapper.toDto(piece)).thenReturn(pieceDto);

        StepVerifier.create(pieceService.getPieceDetailByPieceId(pieceId))
                .expectNext(pieceDto)
                .verifyComplete();

        verify(pieceRepository, times(1)).findById(pieceId);
        verify(pieceDbMapper, times(1)).toDto(piece);
    }
}
