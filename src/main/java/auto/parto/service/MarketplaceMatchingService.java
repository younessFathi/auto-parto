package auto.parto.service;

import auto.parto.dto.PartListingMatchResponse;
import auto.parto.dto.PartListingResponse;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.persistence.PartListingRecord;
import auto.parto.persistence.PartListingRepository;
import auto.parto.persistence.PartRequestRepository;
import auto.parto.validation.PartRequestMatchingShape;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class MarketplaceMatchingService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private final PartRequestRepository requests;
    private final PartListingRepository listings;

    public MarketplaceMatchingService(PartRequestRepository requests, PartListingRepository listings) {
        this.requests = requests;
        this.listings = listings;
    }

    public Mono<PartListingMatchResponse> findMatches(UUID buyerUserPublicId, UUID requestPublicId, int limit) {
        validateLimit(limit);
        return requests.findByPublicId(requestPublicId)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Part request not found")))
                .flatMap(request -> {
                    if (!buyerUserPublicId.equals(request.buyerUserPublicId())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "The authenticated buyer does not own this request"));
                    }
                    if (!"OPEN".equals(request.status())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Only open part requests can be matched"));
                    }
                    PartRequestMatchingShape.requireSupported(
                            name(request.condition()), name(request.category()), request.vehicleMake(),
                            request.vehicleModel(), request.vehicleYear());
                    return listings.findMatches(request, limit)
                            .map(MarketplaceMatchingService::toResponse)
                            .collectList()
                            .map(items -> new PartListingMatchResponse(items, limit));
                });
    }

    public static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private static PartListingResponse toResponse(PartListingRecord r) {
        return new PartListingResponse(r.publicId(), r.sellerUserPublicId(), r.name(), r.description(),
                r.vehicleMake(), r.vehicleModel(), r.vehicleYear(), name(r.condition()), name(r.category()),
                r.amountMinor(), r.currency(), r.businessStatus(), r.deliveryStatus(), r.storageStatus(),
                r.viewCount(), r.createdAt(), r.updatedAt(), r.version());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
