package auto.parto.persistence;

import java.util.UUID;

public record WalletMutationResult(
        WalletRecord wallet,
        UUID ledgerPublicId,
        boolean applied) {
}
