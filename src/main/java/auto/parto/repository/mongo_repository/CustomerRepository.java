package auto.parto.repository.mongo_repository;

import auto.parto.model.Customer;
import auto.parto.repository.ICustomerRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@AllArgsConstructor
@Slf4j
public class CustomerRepository implements ICustomerRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;
    @Override
    public Flux<Customer> findAll() {
        return reactiveMongoTemplate.findAll(Customer.class);
    }

    @Override
    public Mono<Customer> findById(String id) {
        return reactiveMongoTemplate.findById(id, Customer.class);
    }

    @Override
    public Mono<Customer> save(Customer customer) {
        try {
            return reactiveMongoTemplate.save(customer);
        }catch (Exception e){
            log.info(e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Customer> findBy(String value, String field) {
        Query query = new Query(Criteria.where(field).is(value));
        return reactiveMongoTemplate.findOne(query, Customer.class);
    }

    @Override
    public Mono<Boolean> exists(String value, String field) {
        Query query = new Query(Criteria.where(field).is(value));
        return reactiveMongoTemplate.exists(query, Customer.class);
    }

    @Override
    public Mono<Customer> updateGeneratedOtp(String phoneNumber, String sentOtp, String jwtKey, String field) {
        Query query = new Query(Criteria.where(field).is(phoneNumber));

        Update update = new Update();
        if (sentOtp != null) {
            update.set("otpDetails.otp", sentOtp);
            update.set("otpDetails.timestamp", LocalDateTime.now());
            update.set("jwtKey", jwtKey);
        }
        return reactiveMongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    Customer.class
        );
    }
}
