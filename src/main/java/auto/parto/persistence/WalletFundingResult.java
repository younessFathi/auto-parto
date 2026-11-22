package auto.parto.persistence;

import java.util.UUID;

/** Permanent public result, identical on every successful replay of a funding reference. */
public record WalletFundingResult(UUID fundingPublicId, String fundingReference,
                                  UUID beneficiaryUserPublicId, long amountMinor, String currency,
                                  UUID walletPublicId, UUID ledgerPublicId, long balanceAfterMinor,
                                  long walletVersion) {
}
