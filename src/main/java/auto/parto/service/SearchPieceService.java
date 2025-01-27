package auto.parto.service;

import auto.parto.dto.PieceDto;
import auto.parto.mapper.PieceMapper;
import auto.parto.notification.Notification;
import auto.parto.repository.ISearchPieceRepository;
import auto.parto.service.iservice.ISearchPieceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SearchPieceService implements ISearchPieceService {
    private final ISearchPieceRepository searchPieceRepository;
    private final Notification notification;

    public SearchPieceService(ISearchPieceRepository searchPieceRepository ,@Qualifier("pushNotification") Notification notification) {
        this.searchPieceRepository = searchPieceRepository;
        this.notification = notification;
    }

    @Override
    public Mono<PieceDto> searchPieceAndNotifySellers(PieceDto pieceDto) {
        return searchPieceRepository.saveSearchPiece(PieceMapper.toEntity(pieceDto))
                                    .mapNotNull(PieceMapper::toDto)
                                    .doOnNext(notification::notifySellers);
    }
}
