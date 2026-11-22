package auto.parto.dto;

import java.util.UUID;

public record WalletFundingResponse(UUID fundingPublicId, String fundingReference,
                                    UUID beneficiaryUserPublicId, long amountMinor, String currency,
                                    UUID walletPublicId, UUID ledgerPublicId, long balanceAfterMinor,
                                    long walletVersion) {
}
