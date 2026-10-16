package auto.parto.service;

import auto.parto.dto.CreateOfferRequest;
import auto.parto.dto.OfferResponse;
import auto.parto.dto.UpdateOfferStatusRequest;
import auto.parto.enums.OfferStatus;
import auto.parto.persistence.OfferRecord;
import auto.parto.persistence.OfferRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class PostgresqlOfferService {
    private final OfferRepository repository;

    public PostgresqlOfferService(ObjectProvider<OfferRepository> repositoryProvider) {
        this.repository = repositoryProvider.getIfAvailable();
    }

    public Mono<OfferResponse> createForRequest(UUID sellerUserPublicId, UUID requestPublicId,
                                                 CreateOfferRequest request) {
        return requireRepository().createForRequest(sellerUserPublicId, requestPublicId,
                        request.amountMinor(), request.currency(), request.negotiable(),
                        request.deliveryAmountMinor(), request.deliveryMode())
                .map(PostgresqlOfferService::toResponse)
                .switchIfEmpty(notFound());
    }

    public Mono<OfferResponse> createForListing(UUID buyerUserPublicId, UUID listingPublicId,
                                                CreateOfferRequest request) {
        return requireRepository().createForListing(buyerUserPublicId, listingPublicId,
                        request.amountMinor(), request.currency(), request.negotiable(),
                        request.deliveryAmountMinor(), request.deliveryMode())
                .map(PostgresqlOfferService::toResponse)
                .switchIfEmpty(notFound());
    }

    public Mono<OfferResponse> findForActor(UUID offerPublicId, UUID actorPublicId) {
        return requireRepository().findByPublicId(offerPublicId)
                .switchIfEmpty(notFound())
                .flatMap(offer -> {
                    requireActor(offer, actorPublicId);
                    return Mono.just(toResponse(offer));
                });
    }

    public Mono<OfferResponse> updateStatus(UUID offerPublicId, UUID actorPublicId,
                                             UpdateOfferStatusRequest request) {
        return requireRepository().findByPublicId(offerPublicId)
                .switchIfEmpty(notFound())
                .flatMap(existing -> {
                    requireActor(existing, actorPublicId);
                    OfferStatus target = parseStatus(request.status());
                    if (!allowed(existing.status(), target, actorPublicId, existing)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Offer cannot transition from " + existing.status() + " to " + target));
                    }
                    return requireRepository().updateStatus(offerPublicId, actorPublicId, target.name(),
                                    request.expectedVersion(), Instant.now())
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Offer changed before the transition completed")));
                })
                .map(PostgresqlOfferService::toResponse);
    }

    private static <T> Mono<T> notFound() {
        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
    }

    private OfferRepository requireRepository() {
        if (repository == null) {
            throw new IllegalStateException("PostgreSQL marketplace persistence is not enabled");
        }
        return repository;
    }

    private static void requireActor(OfferRecord offer, UUID actorPublicId) {
        if (!actorPublicId.equals(offer.buyerUserPublicId()) && !actorPublicId.equals(offer.sellerUserPublicId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The authenticated principal does not own this offer");
        }
    }

    private static OfferStatus parseStatus(String status) {
        try {
            return OfferStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported offer status");
        }
    }

    private static boolean allowed(String current, OfferStatus target, UUID actorPublicId, OfferRecord offer) {
        if (!OfferStatus.PENDING.name().equals(current)) {
            return false;
        }
        if (OfferStatus.CANCELLED == target) {
            return true;
        }
        return actorPublicId.equals(offer.sellerUserPublicId())
                && (OfferStatus.ACCEPTED == target || OfferStatus.REJECTED == target);
    }

    private static OfferResponse toResponse(OfferRecord offer) {
        return new OfferResponse(offer.publicId(), offer.partRequestPublicId(), offer.partListingPublicId(),
                offer.buyerUserPublicId(), offer.sellerUserPublicId(), offer.amountMinor(), offer.currency(),
                offer.status(), offer.negotiable(), offer.deliveryAmountMinor(), offer.deliveryMode(),
                offer.createdAt(), offer.updatedAt());
    }
}
