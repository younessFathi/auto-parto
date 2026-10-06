package auto.parto.service;

import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.persistence.BuyerProfileRepository;
import auto.parto.service.iservice.IBuyerProfileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.buyer-identity.enabled:false}' == 'true'")
public final class BuyerProfileService implements IBuyerProfileService {

    private final BuyerProfileRepository repository;

    public BuyerProfileService(BuyerProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<BuyerProfileRecord> update(UUID userPublicId, String firstName, String lastName,
                                           String phoneNumber, String pictureUrl) {
        if (userPublicId == null) {
            return Mono.error(new IllegalArgumentException("userPublicId is required"));
        }
        return repository.updateByUserPublicId(userPublicId, firstName, lastName, phoneNumber, pictureUrl);
    }
}
