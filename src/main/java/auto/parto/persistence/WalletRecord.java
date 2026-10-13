package auto.parto.persistence;

import java.util.UUID;

public record WalletRecord(
        UUID publicId,
        UUID ownerUserPublicId,
        long balanceMinor,
        String currency,
        long version) {
}
