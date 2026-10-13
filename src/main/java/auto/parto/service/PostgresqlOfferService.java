package auto.parto.service;

import auto.parto.dto.CreateOfferRequest;
import auto.parto.dto.OfferResponse;
import auto.parto.dto.UpdateOfferStatusRequest;
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
                .filter(offer -> actorPublicId.equals(offer.buyerUserPublicId())
                        || actorPublicId.equals(offer.sellerUserPublicId()))
                .map(PostgresqlOfferService::toResponse)
                .switchIfEmpty(notFound());
    }

    public Mono<OfferResponse> updateStatus(UUID offerPublicId, UUID actorPublicId,
                                             UpdateOfferStatusRequest request) {
        return requireRepository().updateStatus(offerPublicId, actorPublicId, request.status(),
                        request.expectedVersion(), Instant.now())
                .map(PostgresqlOfferService::toResponse)
                .switchIfEmpty(notFound());
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

    private static OfferResponse toResponse(OfferRecord offer) {
        return new OfferResponse(offer.publicId(), offer.partRequestPublicId(), offer.partListingPublicId(),
                offer.buyerUserPublicId(), offer.sellerUserPublicId(), offer.amountMinor(), offer.currency(),
                offer.status(), offer.negotiable(), offer.deliveryAmountMinor(), offer.deliveryMode(),
                offer.createdAt(), offer.updatedAt());
    }
}
