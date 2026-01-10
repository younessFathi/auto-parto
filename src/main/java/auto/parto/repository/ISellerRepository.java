package auto.parto.repository;

import auto.parto.model.Buyer;
import auto.parto.model.Seller;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ISellerRepository {
    Mono<Seller> save(Seller seller);
    Mono<Seller> getSellerById(String id);
    Mono<Seller> findBy(String value, String field);
    Mono<Boolean> exists(String email, String field);
    Mono<Seller> updateGeneratedOtp(String email, String sentOtp, String jwtKey, String field);
    Mono<List<String>> findMatchingSellers();
}
