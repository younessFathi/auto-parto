package auto.parto.service;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.dto.PieceWithFilesDto;
import auto.parto.mapper.PieceDbMapper;
import auto.parto.notification.Notification;
import auto.parto.repository.ISearchPieceRepository;
import auto.parto.service.iservice.IFileService;
import auto.parto.service.iservice.ISearchPieceService;
import auto.parto.validator.ValidCustomerId;
import auto.parto.validator.ValidPieceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
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
    private final IFileService fileService;
    private final PieceDbMapper pieceDbMapper;

    public SearchPieceService(ISearchPieceRepository searchPieceRepository ,
                              @Qualifier("pushNotification") Notification notification,
                              ValidCustomerId validCustomerId,
                              ValidPieceId validPieceId,
                              PieceOfferService pieceOfferService,
                              IFileService fileService, PieceDbMapper pieceDbMapper
    ) {
        this.searchPieceRepository = searchPieceRepository;
        this.notification = notification;
        this.validCustomerId = validCustomerId;
        this.validPieceId = validPieceId;
        this.pieceOfferService = pieceOfferService;
        this.fileService = fileService;
        this.pieceDbMapper = pieceDbMapper;
    }

    @Override
    public Mono<PieceDto> notifySellers(PieceDto pieceDto) {
        return validCustomerId.isValid(pieceDto)
                            .flatMap(notification::notifySellers)
                            .thenReturn(pieceDto);
    }

    @Override
    @Cacheable(value = "piece" , key = "#id")
    public Mono<PieceWithFilesDto> getPieceWithImagesByPieceId(String id) {
        log.info("{} : {}", "Getting piece with images by piece id",  id);
        return searchPieceRepository.getPieceById(id)
                .flatMap(piece->{
                    if (piece.getImagesId() == null || piece.getImagesId().isEmpty())
                        return Mono.just(pieceDbMapper.toDto(piece , null));
                    return fileService.findAllByIds(piece.getImagesId())
                            .collectList()
                            .map(files -> pieceDbMapper.toDto(piece , files));
                });
    }

    @Override
    public Mono<PieceDto> savePieceWithImages(PieceDto pieceDto) {
        log.info("{} : {}", "Saving piece with images",  pieceDto);
        return searchPieceRepository.saveSearchPiece(pieceDbMapper.toEntity(pieceDto))
                                    .map(pieceDbMapper::toDto);
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
