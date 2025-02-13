package auto.parto.service;

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
}
