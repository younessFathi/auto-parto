package auto.parto.service;

import auto.parto.dto.PartRequestCreateRequest;
import auto.parto.dto.PartRequestResponse;
import auto.parto.enums.PartRequestStatus;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.persistence.PartRequestRecord;
import auto.parto.persistence.PartRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class PartRequestService {

    private final PartRequestRepository repository;

    public PartRequestService(PartRequestRepository repository) {
        this.repository = repository;
    }

    public Mono<PartRequestResponse> create(UUID buyerUserPublicId, PartRequestCreateRequest request) {
        return repository.create(buyerUserPublicId, request.name(), request.description(), request.condition(),
                        request.category(), request.vehicleMake(), request.vehicleModel(), request.vehicleYear())
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Buyer account not found")))
                .map(PartRequestService::toResponse);
    }

    public Flux<PartRequestResponse> findMine(UUID buyerUserPublicId) {
        return repository.findByBuyerUserPublicId(buyerUserPublicId).map(PartRequestService::toResponse);
    }

    public Mono<PartRequestResponse> close(UUID buyerUserPublicId, UUID publicId) {
        return transition(buyerUserPublicId, publicId, PartRequestStatus.CLOSED);
    }

    public Mono<PartRequestResponse> cancel(UUID buyerUserPublicId, UUID publicId) {
        return transition(buyerUserPublicId, publicId, PartRequestStatus.CANCELLED);
    }

    private Mono<PartRequestResponse> transition(UUID buyerUserPublicId, UUID publicId, PartRequestStatus target) {
        return repository.findByPublicId(publicId)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Part request not found")))
                .flatMap(existing -> {
                    requireOwner(existing, buyerUserPublicId);
                    if (!PartRequestStatus.OPEN.name().equals(existing.status())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Part request cannot transition from " + existing.status()));
                    }
                    return repository.transition(publicId, buyerUserPublicId, target.name())
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Part request changed before the transition completed")));
                })
                .map(PartRequestService::toResponse);
    }

    private static void requireOwner(PartRequestRecord request, UUID buyerUserPublicId) {
        if (!request.buyerUserPublicId().equals(buyerUserPublicId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The authenticated buyer does not own this request");
        }
    }

    private static PartRequestResponse toResponse(PartRequestRecord record) {
        return new PartRequestResponse(record.publicId(), record.buyerUserPublicId(), record.name(),
                record.description(), record.condition(), record.category(), record.vehicleMake(),
                record.vehicleModel(), record.vehicleYear(), record.status(), record.createdAt(),
                record.updatedAt(), record.version());
    }
}
