package auto.parto.repository.mongo_repository;

import auto.parto.model.Buyer;
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
public class BuyerRepository implements ICustomerRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;
    @Override
    public Flux<Buyer> findAll() {
        return reactiveMongoTemplate.findAll(Buyer.class);
    }

    @Override
    public Mono<Buyer> findById(String id) {
        return reactiveMongoTemplate.findById(id, Buyer.class);
    }

    @Override
    public Mono<Buyer> save(Buyer customer) {
        try {
            return reactiveMongoTemplate.save(customer);
        }catch (Exception e){
            log.info(e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Buyer> findBy(String value, String field) {
        Query query = new Query(Criteria.where(field).is(value));
        return reactiveMongoTemplate.findOne(query, Buyer.class);
    }

    @Override
    public Mono<Boolean> exists(String value, String field) {
        Query query = new Query(Criteria.where(field).is(value));
        return reactiveMongoTemplate.exists(query, Buyer.class);
    }

    @Override
    public Mono<Boolean> existsById(String id) {
        return findByIdAndIsActiveTrue(id).hasElement();
    }

    @Override
    public Mono<Buyer> updateGeneratedOtp(String phoneNumber, String sentOtp, String jwtKey, String field) {
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
                    Buyer.class
        );
    }

    @Override
    public Mono<Buyer> deactivateCustomer(String id) {
        return findByIdAndIsActiveTrue(id).flatMap(customer -> {
            customer.setIsActive(Boolean.FALSE);
            return reactiveMongoTemplate.save(customer);
        });
    }

    @Override
    public Mono<Buyer> findByIdAndIsActiveTrue(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id).and("isActive").is(true));
        return reactiveMongoTemplate.findOne(query , Buyer.class);
    }
}
