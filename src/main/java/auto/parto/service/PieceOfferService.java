package auto.parto.service;

import auto.parto.dto.PieceOfferDto;
import auto.parto.mapper.PieceOfferMapper;
import auto.parto.repository.mongo_repository.PieceOfferRepository;
import auto.parto.service.iservice.IPieceOfferService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class PieceOfferService implements IPieceOfferService {
    private final PieceOfferRepository pieceOfferRepository;

    @Override
    public Mono<PieceOfferDto> save(PieceOfferDto pieceOfferDto) {
        return pieceOfferRepository.save(PieceOfferMapper.toEntity(pieceOfferDto))
                                    .map(PieceOfferMapper::toDto);
    }
}
