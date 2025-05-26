package auto.parto.service;

import auto.parto.dto.PieceOfferDto;
import auto.parto.mapper.PieceOfferDbMapper;
import auto.parto.repository.mongo_repository.PieceOfferRepository;
import auto.parto.service.iservice.IPieceOfferService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class PieceOfferService implements IPieceOfferService {
    private final PieceOfferRepository pieceOfferRepository;
    private final PieceOfferDbMapper pieceOfferDbMapper;

    @Override
    public Mono<PieceOfferDto> save(PieceOfferDto pieceOfferDto) {
        return pieceOfferRepository.save(pieceOfferDbMapper.toEntity(pieceOfferDto))
                                    .map(pieceOfferDbMapper::toDto);
    }

    @Override
    public Mono<PieceOfferDto> create(PieceOfferDto pieceOfferDto) {
        return pieceOfferRepository.save(pieceOfferDbMapper.toEntity(pieceOfferDto))
                                    .map(pieceOfferDbMapper::toDto);
    }

    @Override
    public Mono<PieceOfferDto> update(PieceOfferDto pieceOfferDto) {
        return pieceOfferRepository.update(pieceOfferDto.getId() , pieceOfferDto.getImagesId())
                                    .map(pieceOfferDbMapper::toDto);
    }
}
