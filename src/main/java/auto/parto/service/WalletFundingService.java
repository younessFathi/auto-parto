package auto.parto.service;

import auto.parto.dto.WalletFundingRequest;
import auto.parto.persistence.WalletFundingRepository;
import auto.parto.dto.WalletFundingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class WalletFundingService {
    private final WalletFundingRepository repository;

    public WalletFundingService(ObjectProvider<WalletFundingRepository> repositories) {
        this.repository = repositories.getIfAvailable();
    }

    public Mono<WalletFundingResponse> confirm(UUID administratorPublicId, WalletFundingRequest request,
                                             String idempotencyKey) {
        if (!request.confirmed()) return Mono.error(new IllegalArgumentException("Funding must be confirmed"));
        if (repository == null) return Mono.error(new IllegalStateException("Wallet funding is unavailable"));
        return repository.confirm(administratorPublicId, request.beneficiaryUserPublicId(),
                request.amountMinor(), request.currency().name(), request.fundingReference(), idempotencyKey)
                .map(result -> new WalletFundingResponse(result.fundingPublicId(), result.fundingReference(),
                        result.beneficiaryUserPublicId(), result.amountMinor(), result.currency(),
                        result.walletPublicId(), result.ledgerPublicId(), result.balanceAfterMinor(), result.walletVersion()));
    }
}
