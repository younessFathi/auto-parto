package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SellerProfileRepository {

    Mono<SellerProfileRecord> findByEmail(String email);

    Mono<SellerProfileRecord> findByUserPublicId(UUID userPublicId);

    Mono<SellerProfileRecord> updateByUserPublicId(UUID userPublicId, String firstName, String lastName,
                                                   String phoneNumber, String partType, String description);
}
