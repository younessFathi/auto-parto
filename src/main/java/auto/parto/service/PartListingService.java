package auto.parto.service;

import auto.parto.dto.PartListingCreateRequest;
import auto.parto.dto.PartListingPageResponse;
import auto.parto.dto.PartListingResponse;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.persistence.PartListingRecord;
import auto.parto.persistence.PartListingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class PartListingService {
    private final PartListingRepository repository;

    public PartListingService(PartListingRepository repository) { this.repository = repository; }

    public Mono<PartListingResponse> create(UUID sellerId, PartListingCreateRequest request) {
        return repository.create(sellerId, request.name(), request.description(), request.condition(),
                        request.category(), request.vehicleMake(), request.vehicleModel(), request.vehicleYear(),
                        request.amountMinor(), request.currency())
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Seller account not found")))
                .map(PartListingService::toResponse);
    }

    public Mono<PartListingPageResponse> findMine(UUID sellerId, int page, int size) {
        return repository.findBySellerPublicId(sellerId, page, size).map(PartListingService::toResponse)
                .collectList().zipWith(repository.countBySellerPublicId(sellerId))
                .map(result -> new PartListingPageResponse(result.getT1(), result.getT2(), page, size));
    }

    public Mono<PartListingResponse> transition(UUID sellerId, UUID publicId, String target) {
        return repository.findByPublicId(publicId)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Part listing not found")))
                .flatMap(existing -> {
                    if (!sellerId.equals(existing.sellerUserPublicId())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "The authenticated seller does not own this listing"));
                    }
                    if (!allowed(existing.businessStatus(), target)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Part listing cannot transition from " + existing.businessStatus()));
                    }
                    return repository.updateStatus(publicId, sellerId, target, existing.version(), Instant.now())
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Part listing changed before transition completed")));
                }).map(PartListingService::toResponse);
    }

    private static boolean allowed(String current, String target) {
        return switch (current) {
            case "DRAFT" -> target.equals("PUBLISHED") || target.equals("CLOSED");
            case "PUBLISHED" -> target.equals("PAUSED") || target.equals("RESERVED") || target.equals("CLOSED");
            case "PAUSED" -> target.equals("PUBLISHED") || target.equals("CLOSED");
            case "RESERVED" -> target.equals("CLOSED");
            default -> false;
        };
    }

    private static PartListingResponse toResponse(PartListingRecord r) {
        return new PartListingResponse(r.publicId(), r.sellerUserPublicId(), r.name(), r.description(),
                r.vehicleMake(), r.vehicleModel(), r.vehicleYear(),
                name(r.condition()), name(r.category()), r.amountMinor(), r.currency(), r.businessStatus(),
                r.deliveryStatus(), r.storageStatus(), r.viewCount(), r.createdAt(), r.updatedAt(), r.version());
    }

    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
