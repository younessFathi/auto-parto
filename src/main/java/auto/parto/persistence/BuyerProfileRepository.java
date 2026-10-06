package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BuyerProfileRepository {

    Mono<BuyerProfileRecord> findByUserPublicId(UUID userPublicId);

    Mono<BuyerProfileRecord> updateByUserPublicId(UUID userPublicId, String firstName, String lastName,
                                                  String phoneNumber, String pictureUrl);
}
