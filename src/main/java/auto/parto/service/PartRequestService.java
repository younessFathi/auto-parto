package auto.parto.service;

import auto.parto.dto.PartRequestCreateRequest;
import auto.parto.dto.PartRequestPageResponse;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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

    public Mono<PartRequestPageResponse> findMine(UUID buyerUserPublicId, String cursor, int limit) {
        Cursor decodedCursor = decodeCursor(cursor);
        return repository.findByBuyerUserPublicId(buyerUserPublicId,
                        decodedCursor == null ? null : decodedCursor.createdAt(),
                        decodedCursor == null ? null : decodedCursor.publicId(),
                        limit + 1)
                .map(PartRequestService::toResponse)
                .collectList()
                .map(records -> toPage(records, limit));
    }

    public static void validateCursor(String cursor) {
        decodeCursor(cursor);
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
                record.description(), enumName(record.condition()), enumName(record.category()), record.vehicleMake(),
                record.vehicleModel(), record.vehicleYear(), record.status(), record.createdAt(),
                record.updatedAt(), record.version());
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static PartRequestPageResponse toPage(List<PartRequestResponse> records, int limit) {
        boolean hasNext = records.size() > limit;
        List<PartRequestResponse> items = hasNext ? records.subList(0, limit) : records;
        String nextCursor = hasNext ? encodeCursor(items.get(items.size() - 1)) : null;
        return new PartRequestPageResponse(items, nextCursor);
    }

    private static String encodeCursor(PartRequestResponse item) {
        String value = item.createdAt() + "|" + item.publicId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            if (encoded.isBlank()) {
                throw new IllegalArgumentException("cursor must not be blank");
            }
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("cursor has an invalid format");
            }
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor is malformed");
        }
    }

    private record Cursor(Instant createdAt, UUID publicId) {
    }
}
