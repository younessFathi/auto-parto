package auto.parto.service.iservice;

import auto.parto.persistence.SellerProfileRecord;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ISellerProfileService {

    Mono<SellerProfileRecord> update(UUID userPublicId, String firstName, String lastName,
                                     String phoneNumber, String partType, String description);
}
