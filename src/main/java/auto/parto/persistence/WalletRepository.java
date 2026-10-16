package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface WalletRepository {

    Mono<WalletRecord> findByOwnerPublicId(UUID ownerUserPublicId, String currency);

    Mono<WalletMutationResult> mutate(UUID ownerUserPublicId, String currency, long amountMinor,
                                      String operation, String idempotencyKey, String externalReference);

    Mono<Long> purgeExpiredIdempotencyRecords(Instant now, int limit);
}
