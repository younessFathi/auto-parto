package auto.parto.service;

import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import auto.parto.enums.SellerType;
import auto.parto.enums.Status;
import auto.parto.persistence.PartRequestRecord;
import auto.parto.persistence.PartRequestRepository;
import auto.parto.service.iservice.IPieceService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

@Service
public class PieceService implements IPieceService {
    private final PartRequestRepository repository;
    public PieceService(PartRequestRepository repository) { this.repository = repository; }
    @Override public Mono<Boolean> checkIfPieceById(String id) { return repository.findByPublicId(requireUuid(id)).hasElement(); }
    @Override @Caching(evict = {@CacheEvict(value = "pieces", allEntries = true), @CacheEvict(value = "pieceDetails", allEntries = true)})
    public Mono<PieceDto> create(PieceDto pieceDto) { return repository.create(requireUuid(pieceDto.getCustomerId()), pieceDto.getName(), pieceDto.getDescription(), valueOf(pieceDto.getCondition()), valueOf(pieceDto.getCategory())).map(PieceService::toDto); }
    @Override @Cacheable(value = "pieces", key = "#id + ':' + #page + ':' + #size")
    public Mono<PagedResponse<PieceDto>> getAllPiecesByCustomerId(String id, int page, int size) { UUID buyerId = requireUuid(id); return repository.findByBuyerPublicId(buyerId, page, size).map(PieceService::toDto).collectList().zipWith(repository.countByBuyerPublicId(buyerId)).map(result -> { PagedResponse<PieceDto> response = new PagedResponse<>(); response.setData(result.getT1()); response.setPage(page); response.setSize(size); response.setTotalItems(result.getT2()); return response; }); }
    @Override @Cacheable(value = "pieceDetails", key = "#pieceId")
    public Mono<PieceDto> getPieceDetailByPieceId(String pieceId) { return repository.findByPublicId(requireUuid(pieceId)).map(PieceService::toDto); }
    private static PieceDto toDto(PartRequestRecord record) { return PieceDto.builder().id(record.publicId().toString()).customerId(record.buyerUserPublicId().toString()).name(record.name()).description(record.description()).condition(record.condition()).category(record.category()).createAt(record.createdAt() == null ? null : record.createdAt().atZone(ZoneOffset.UTC).toLocalDateTime()).modifiedAt(record.updatedAt() == null ? null : record.updatedAt().atZone(ZoneOffset.UTC).toLocalDateTime()).imagesId(Collections.emptyList()).seeNumber(Math.toIntExact(record.viewCount())).sellerType(SellerType.NEW).sellerRank(0D).status(toStatus(record.status())).build(); }
    private static Status toStatus(String status) { return switch (status) { case "OPEN" -> Status.PENDING; case "MATCHED" -> Status.IN_PROGRESS; case "CLOSED" -> Status.COMPLETED; case "CANCELLED" -> Status.CANCELLED; default -> Status.FAILED; }; }
    private static String valueOf(Enum<?> value) { return value == null ? null : value.name(); }
    private static UUID requireUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new IllegalArgumentException("canonical ID must be a UUID", e); } }
}
