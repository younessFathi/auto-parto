package auto.parto.service;

import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import auto.parto.enums.SellerType;
import auto.parto.enums.Status;
import auto.parto.mapper.PieceDbMapper;
import auto.parto.persistence.PartRequestRecord;
import auto.parto.persistence.PartRequestRepository;
import auto.parto.repository.IPieceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import auto.parto.service.iservice.IPieceService;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

@Service
public class PieceService implements IPieceService {
    private final IPieceRepository pieceRepository;
    private final PieceDbMapper pieceDbMapper;
    private final PartRequestRepository partRequestRepository;

    public PieceService(IPieceRepository pieceRepository, PieceDbMapper pieceDbMapper) {
        this(pieceRepository, pieceDbMapper, (PartRequestRepository) null);
    }

    @Autowired
    public PieceService(IPieceRepository pieceRepository, PieceDbMapper pieceDbMapper,
                        ObjectProvider<PartRequestRepository> partRequestRepositoryProvider) {
        this(pieceRepository, pieceDbMapper, partRequestRepositoryProvider.getIfAvailable());
    }

    public PieceService(IPieceRepository pieceRepository, PieceDbMapper pieceDbMapper,
                        PartRequestRepository partRequestRepository) {
        this.pieceRepository = pieceRepository;
        this.pieceDbMapper = pieceDbMapper;
        this.partRequestRepository = partRequestRepository;
    }

    @Override
    public Mono<Boolean> checkIfPieceById(String id) {
        UUID publicId = parseUuid(id);
        if (partRequestRepository != null && publicId != null) {
            return partRequestRepository.findByPublicId(publicId).hasElement();
        }
        return pieceRepository.checkIfPieceById(id);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "pieces", allEntries = true),
            @CacheEvict(value = "pieceDetails", allEntries = true)
    })
    public Mono<PieceDto> create(PieceDto pieceDto) {
        UUID buyerPublicId = parseUuid(pieceDto.getCustomerId());
        if (partRequestRepository != null && buyerPublicId != null) {
            return partRequestRepository.create(buyerPublicId, pieceDto.getName(), pieceDto.getDescription(),
                            valueOf(pieceDto.getCondition()), valueOf(pieceDto.getCategory()))
                    .map(this::toDto);
        }
        return pieceRepository.save(pieceDbMapper.toEntity(pieceDto)).map(pieceDbMapper::toDto);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "pieces", allEntries = true),
            @CacheEvict(value = "pieceDetails", key = "#pieceDto.id")
    })
    public Mono<PieceDto> update(PieceDto pieceDto) {
        return pieceRepository.update(pieceDto.getId() , pieceDto.getImagesId()).map(pieceDbMapper::toDto);
    }

    @Override
    @Cacheable(value = "pieces" , key = "#id + ':' + #page + ':' + #size")
    public Mono<PagedResponse<PieceDto>> getAllPiecesByCustomerId(String id, int page, int size) {
        UUID buyerPublicId = parseUuid(id);
        if (partRequestRepository != null && buyerPublicId != null) {
            return partRequestRepository.findByBuyerPublicId(buyerPublicId, page, size)
                    .map(this::toDto)
                    .collectList()
                    .zipWith(partRequestRepository.countByBuyerPublicId(buyerPublicId))
                    .map(result -> {
                        PagedResponse<PieceDto> response = new PagedResponse<>();
                        response.setData(result.getT1());
                        response.setPage(page);
                        response.setSize(size);
                        response.setTotalItems(result.getT2());
                        return response;
                    });
        }
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
        UUID publicId = parseUuid(pieceId);
        if (partRequestRepository != null && publicId != null) {
            return partRequestRepository.findByPublicId(publicId).map(this::toDto);
        }
        return pieceRepository.findById(pieceId).map(pieceDbMapper::toDto);
    }

    private PieceDto toDto(PartRequestRecord record) {
        return PieceDto.builder()
                .id(record.publicId().toString())
                .customerId(record.buyerUserPublicId().toString())
                .name(record.name())
                .description(record.description())
                .condition(record.condition())
                .category(record.category())
                .createAt(record.createdAt() == null ? null : record.createdAt().atZone(ZoneOffset.UTC).toLocalDateTime())
                .modifiedAt(record.updatedAt() == null ? null : record.updatedAt().atZone(ZoneOffset.UTC).toLocalDateTime())
                .imagesId(Collections.emptyList())
                .seeNumber(Math.toIntExact(record.viewCount()))
                .sellerType(SellerType.NEW)
                .sellerRank(0D)
                .status(toStatus(record.status()))
                .build();
    }

    private static Status toStatus(String status) {
        return switch (status) {
            case "OPEN" -> Status.PENDING;
            case "MATCHED" -> Status.IN_PROGRESS;
            case "CLOSED" -> Status.COMPLETED;
            case "CANCELLED" -> Status.CANCELLED;
            default -> Status.FAILED;
        };
    }

    private static String valueOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
