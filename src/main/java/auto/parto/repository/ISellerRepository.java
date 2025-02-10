package auto.parto.repository;

import auto.parto.model.Seller;
import reactor.core.publisher.Mono;

public interface ISellerRepository {
    Mono<Seller> save(Seller seller);
}
