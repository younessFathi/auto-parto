package auto.parto.service;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.mapper.PieceMapper;
import auto.parto.notification.Notification;
import auto.parto.repository.ISearchPieceRepository;
import auto.parto.service.iservice.ISearchPieceService;
import auto.parto.validator.ValidCustomerId;
import auto.parto.validator.ValidPieceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class SearchPieceService implements ISearchPieceService {
    private final ISearchPieceRepository searchPieceRepository;
    private final Notification notification;
    private final ValidCustomerId validCustomerId;
    private final ValidPieceId validPieceId;
    private final PieceOfferService pieceOfferService;

    public SearchPieceService(ISearchPieceRepository searchPieceRepository ,
                              @Qualifier("pushNotification") Notification notification,
                              ValidCustomerId validCustomerId,
                              ValidPieceId validPieceId,
                              PieceOfferService pieceOfferService
    ) {
        this.searchPieceRepository = searchPieceRepository;
        this.notification = notification;
        this.validCustomerId = validCustomerId;
        this.validPieceId = validPieceId;
        this.pieceOfferService = pieceOfferService;
    }

    @Override
    public Mono<PieceDto> notifySellers(PieceDto pieceDto) {
        return validCustomerId.isValid(pieceDto)
                            .flatMap(notification::notifySellers)
                            .thenReturn(pieceDto);
    }

    @Override
    public Mono<PieceDto> getPieceWithImagesByPieceId(String id) {
        log.info("{} : {}", "Getting piece with images by piece id",  id);
        return searchPieceRepository.getPieceById(id)
                                    .mapNotNull(PieceMapper::toDto);
    }

    @Override
    public Mono<PieceDto> savePieceWithImages(PieceDto pieceDto) {
        log.info("{} : {}", "Saving piece with images",  pieceDto);
        return searchPieceRepository.saveSearchPiece(PieceMapper.toEntity(pieceDto))
                                    .map(PieceMapper::toDto);
    }

    @Override
    public Mono<PieceOfferDto> notifyCustomer(PieceOfferDto pieceOfferDto) {
        return validPieceId.isValid(pieceOfferDto)
                           .flatMap(validCustomerId::isValid)
                           .flatMap(notification::notifyCustomer);
    }

    @Override
    public Mono<PieceOfferDto> createOffer(PieceOfferDto pieceOfferDto) {
        log.info("pieceOffer : {}" , pieceOfferDto);
        return pieceOfferService.save(pieceOfferDto);
    }

    @Override
    public Mono<Boolean> checkIfPieceById(String id) {
        return searchPieceRepository.getPieceById(id).hasElement();
    }
}
