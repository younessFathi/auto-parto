package auto.parto.repository.mongo_repository;

import auto.parto.model.Seller;
import auto.parto.repository.ISellerRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@AllArgsConstructor
public class SellerRepository implements ISellerRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<Seller> save(Seller seller) {
        return reactiveMongoTemplate.save(seller);
    }

    @Override
    public Mono<Seller> getSellerById(String id) {
        return reactiveMongoTemplate.findById(id, Seller.class);
    }
}
