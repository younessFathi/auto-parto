package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface WalletFundingRepository {
    Mono<WalletFundingResult> confirm(UUID administratorPublicId, UUID beneficiaryPublicId,
                                      long amountMinor, String currency, String fundingReference,
                                      String idempotencyKey);
}
