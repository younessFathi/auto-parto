package auto.parto.service;

import auto.parto.persistence.SellerProfileRecord;
import auto.parto.persistence.SellerProfileRepository;
import auto.parto.service.iservice.ISellerProfileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.enabled:false}' == 'true' && "
        + "'${auto.parto.persistence.postgresql.identity.enabled:false}' == 'true'")
public final class SellerProfileService implements ISellerProfileService {

    private final SellerProfileRepository repository;

    public SellerProfileService(SellerProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<SellerProfileRecord> update(UUID userPublicId, String firstName, String lastName,
                                            String phoneNumber, String partType, String description) {
        if (userPublicId == null) {
            return Mono.error(new IllegalArgumentException("userPublicId is required"));
        }
        return repository.updateByUserPublicId(userPublicId, firstName, lastName, phoneNumber,
                partType, description);
    }
}
