package auto.parto.repository;

import auto.parto.model.Customer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ICustomerRepository {
    Flux<Customer> findAll();
    Mono<Customer> findById(String id);
    Mono<Customer> save(Customer user);
    Mono<Customer> findBy(String phoneNumber, String field);
    Mono<Boolean> exists(String phoneNumber, String field);
    Mono<Customer> updateGeneratedOtp(String phoneNumber, String sentOtp, String s, String field);
}
