package auto.parto.repository.mongo_repository;

import auto.parto.model.Buyer;
import auto.parto.otp.OtpCodeHasher;
import auto.parto.repository.ICustomerRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@AllArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover", name = "mongo-disabled",
        havingValue = "false", matchIfMissing = true)
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
    public Mono<Buyer> updateGeneratedOtp(String phoneNumber, String sentOtp, String field, LocalDateTime expiresAt) {
        Query query = new Query(Criteria.where(field).is(phoneNumber));

        Update update = new Update();
        if (sentOtp != null) {
            LocalDateTime now = LocalDateTime.now();
            update.set("otpDetails.otpHash", OtpCodeHasher.hashCode(sentOtp));
            update.unset("otpDetails.otp");
            update.set("otpDetails.purpose", "LOGIN");
            update.set("otpDetails.timestamp", now);
            update.set("otpDetails.expiresAt", expiresAt);
            update.unset("otpDetails.consumedAt");
            update.set("otpDetails.failedAttempts", 0);
            update.unset("otpDetails.lockedUntil");
        }
        return reactiveMongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    Buyer.class
        );
    }

    @Override
    public Mono<Boolean> consumeValidOtp(String email, String otp, LocalDateTime now) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("email").is(email),
                Criteria.where("isActive").is(false),
                Criteria.where("otpDetails.purpose").is("LOGIN"),
                Criteria.where("otpDetails.identity").is(email),
                Criteria.where("otpDetails.consumedAt").exists(false),
                Criteria.where("otpDetails.expiresAt").gt(now),
                new Criteria().orOperator(
                        Criteria.where("otpDetails.lockedUntil").exists(false),
                        Criteria.where("otpDetails.lockedUntil").lte(now))));
        return reactiveMongoTemplate.findOne(query, Buyer.class)
                .filter(candidate -> candidate.getOtpDetails() != null
                        && OtpCodeHasher.matchesCode(otp, candidate.getOtpDetails().getOtpHash()))
                .flatMap(candidate -> reactiveMongoTemplate.findAndModify(
                        new Query(new Criteria().andOperator(
                                Criteria.where("_id").is(candidate.getId()),
                                Criteria.where("otpDetails.consumedAt").exists(false))),
                        new Update().set("otpDetails.consumedAt", now),
                        FindAndModifyOptions.options().returnNew(true), Buyer.class))
                .hasElement();
    }

    @Override
    public Mono<Buyer> recordFailedOtpAttempt(String email, LocalDateTime now) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("email").is(email),
                Criteria.where("otpDetails.purpose").is("LOGIN"),
                Criteria.where("otpDetails.identity").is(email),
                Criteria.where("otpDetails.consumedAt").exists(false)));
        return reactiveMongoTemplate.findAndModify(query,
                        new Update().inc("otpDetails.failedAttempts", 1),
                        FindAndModifyOptions.options().returnNew(true), Buyer.class)
                .flatMap(updated -> updated.getOtpDetails() != null
                        && updated.getOtpDetails().getFailedAttempts() >= 5
                        ? reactiveMongoTemplate.findAndModify(query,
                                new Update().set("otpDetails.lockedUntil", now.plusMinutes(15)),
                                FindAndModifyOptions.options().returnNew(true), Buyer.class)
                        : Mono.just(updated));
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
