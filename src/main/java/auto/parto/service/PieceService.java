package auto.parto.service;

import auto.parto.dto.PieceDto;
import auto.parto.mapper.PieceMapper;
import auto.parto.repository.IPieceRepository;
import auto.parto.service.iservice.IPieceService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class PieceService implements IPieceService {
    private final IPieceRepository pieceRepository;
    @Override
    public Mono<Boolean> checkIfPieceById(String id) {
        return pieceRepository.checkIfPieceById(id);
    }

    @Override
    public Mono<PieceDto> create(PieceDto pieceDto) {
        return pieceRepository.save(PieceMapper.toEntity(pieceDto)).map(PieceMapper::toDto);
    }

    @Override
    public Mono<PieceDto> update(PieceDto pieceDto) {
        return pieceRepository.update(pieceDto.getId() , pieceDto.getImagesId()).map(PieceMapper::toDto);
    }
}
