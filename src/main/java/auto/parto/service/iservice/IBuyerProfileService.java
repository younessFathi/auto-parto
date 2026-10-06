package auto.parto.service.iservice;

import auto.parto.persistence.BuyerProfileRecord;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IBuyerProfileService {

    Mono<BuyerProfileRecord> update(UUID userPublicId, String firstName, String lastName,
                                    String phoneNumber, String pictureUrl);
}
