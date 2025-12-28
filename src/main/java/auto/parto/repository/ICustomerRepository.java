package auto.parto.repository;

import auto.parto.model.Buyer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ICustomerRepository {
    Flux<Buyer> findAll();
    Mono<Buyer> findById(String id);
    Mono<Buyer> save(Buyer user);
    Mono<Buyer> findBy(String phoneNumber, String field);
    Mono<Boolean> exists(String phoneNumber, String field);
    Mono<Boolean> existsById(String id);
    Mono<Buyer> updateGeneratedOtp(String phoneNumber, String sentOtp, String s, String field);
    Mono<Buyer> deactivateCustomer(String id);
    Mono<Buyer> findByIdAndIsActiveTrue(String id);
}
