package auto.parto.service;

import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import auto.parto.mapper.PieceDbMapper;
import auto.parto.repository.IPieceRepository;
import auto.parto.service.iservice.IPieceService;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class PieceService implements IPieceService {
    private final IPieceRepository pieceRepository;
    private final PieceDbMapper pieceDbMapper;
    @Override
    public Mono<Boolean> checkIfPieceById(String id) {
        return pieceRepository.checkIfPieceById(id);
    }

    @Override
    public Mono<PieceDto> create(PieceDto pieceDto) {
        return pieceRepository.save(pieceDbMapper.toEntity(pieceDto)).map(pieceDbMapper::toDto);
    }

    @Override
    public Mono<PieceDto> update(PieceDto pieceDto) {
        return pieceRepository.update(pieceDto.getId() , pieceDto.getImagesId()).map(pieceDbMapper::toDto);
    }

    @Override
    @Cacheable(value = "pieces" , key = "#id + ':' + #page + ':' + #size")
    public Mono<PagedResponse<PieceDto>> getAllPiecesByCustomerId(String id, int page, int size) {
        return pieceRepository.getAllPieces(id , page , size)
                              .map(pieceDbMapper::toDto)
                              .collectList()
                              .zipWith(pieceRepository.getTotalPieces(id))
                              .map(result -> {
                                  PagedResponse<PieceDto> response = new PagedResponse<>();
                                  response.setData(result.getT1());
                                  response.setPage(page);
                                  response.setSize(size);
                                  response.setTotalItems(result.getT2());
                                  return response;
                              });
    }

    @Override
    @Cacheable(value = "pieceDetails" , key = "#pieceId")
    public Mono<PieceDto> getPieceDetailByPieceId(String pieceId) {
        return pieceRepository.findById(pieceId).map(pieceDbMapper::toDto);
    }
}
