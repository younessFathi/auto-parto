package auto.parto.repository.mongo_repository;

import auto.parto.model.Buyer;
import auto.parto.model.Seller;
import auto.parto.otp.OtpCodeHasher;
import auto.parto.repository.ISellerRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@AllArgsConstructor
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover", name = "mongo-disabled",
        havingValue = "false", matchIfMissing = true)
public class SellerRepository implements ISellerRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<Seller> save(Seller seller) {
        return reactiveMongoTemplate.save(seller);
    }

    @Override
    public Mono<Seller> getSellerById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id).and("isActive").is(true));
        return reactiveMongoTemplate.findOne(query , Seller.class);
    }

    @Override
    public Mono<Seller> findBy(String value, String field) {
        Query query = new Query(Criteria.where(field).is(value));
        return reactiveMongoTemplate.findOne(query, Seller.class);
    }

    @Override
    public Mono<Boolean> exists(String value, String field) {
        Query query = new Query(Criteria.where(field).is(value));
        return reactiveMongoTemplate.exists(query, Seller.class);
    }

    @Override
    public Mono<Seller> updateGeneratedOtp(String email, String sentOtp, String field, LocalDateTime expiresAt) {
        Query query = new Query(Criteria.where(field).is(email));

        Update update = new Update();
        if (sentOtp != null) {
            LocalDateTime now = LocalDateTime.now();
            update.set("otpDetails.otpHash", OtpCodeHasher.hashCode(sentOtp));
            update.unset("otpDetails.otp");
            update.set("otpDetails.purpose", "LOGIN");
            update.set("otpDetails.identity", email);
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
                Seller.class
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
        return reactiveMongoTemplate.findOne(query, Seller.class)
                .filter(candidate -> candidate.getOtpDetails() != null
                        && OtpCodeHasher.matchesCode(otp, candidate.getOtpDetails().getOtpHash()))
                .flatMap(candidate -> reactiveMongoTemplate.findAndModify(
                        new Query(new Criteria().andOperator(
                                Criteria.where("_id").is(candidate.getId()),
                                Criteria.where("email").is(email),
                                Criteria.where("isActive").is(false),
                                Criteria.where("otpDetails.purpose").is("LOGIN"),
                                Criteria.where("otpDetails.identity").is(email),
                                Criteria.where("otpDetails.otpHash").is(candidate.getOtpDetails().getOtpHash()),
                                Criteria.where("otpDetails.consumedAt").exists(false),
                                Criteria.where("otpDetails.expiresAt").gt(now),
                                new Criteria().orOperator(
                                        Criteria.where("otpDetails.lockedUntil").exists(false),
                                        Criteria.where("otpDetails.lockedUntil").lte(now)),
                                Criteria.where("otpDetails.failedAttempts").lt(5))),
                        new Update().set("otpDetails.consumedAt", now),
                        FindAndModifyOptions.options().returnNew(true), Seller.class))
                .hasElement();
    }

    @Override
    public Mono<Seller> recordFailedOtpAttempt(String email, LocalDateTime now) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("email").is(email),
                Criteria.where("otpDetails.purpose").is("LOGIN"),
                Criteria.where("otpDetails.identity").is(email),
                Criteria.where("otpDetails.consumedAt").exists(false),
                Criteria.where("otpDetails.expiresAt").gt(now),
                new Criteria().orOperator(
                        Criteria.where("otpDetails.lockedUntil").exists(false),
                        Criteria.where("otpDetails.lockedUntil").lte(now)),
                Criteria.where("otpDetails.failedAttempts").lt(5)));
        AggregationUpdate update = AggregationUpdate.update()
                .set("otpDetails.failedAttempts")
                .toValueOf(ArithmeticOperators.valueOf("otpDetails.failedAttempts").add(1))
                .set("otpDetails.lockedUntil")
                .toValue(ConditionalOperators.when(
                                ComparisonOperators.valueOf("otpDetails.failedAttempts").greaterThanEqualToValue(5))
                        .then(now.plusMinutes(15))
                        .otherwiseValueOf("otpDetails.lockedUntil"));
        return reactiveMongoTemplate.findAndModify(query,
                        update,
                        FindAndModifyOptions.options().returnNew(true), Seller.class)
                ;
    }

    @Override
    public Mono<Seller> addFunds(String id, long amountMinor) {
        if (amountMinor == Long.MIN_VALUE) {
            return Mono.error(new IllegalArgumentException("amountMinor is outside the supported debit range"));
        }
        Query query = new Query(Criteria.where("_id").is(id).and("isActive").is(true));
        if (amountMinor < 0) {
            query.addCriteria(Criteria.where("wallet.balanceMinor").gte(Math.abs(amountMinor)));
        }
        Update update = new Update().inc("wallet.balanceMinor", amountMinor);
        return reactiveMongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Seller.class
        );
    }

    @Override
    public Mono<List<String>> findMatchingSellers() {
        Query query = new Query();
        query.addCriteria(Criteria.where("isActive").is(true));
        query.fields().include("_id");
        return reactiveMongoTemplate.find(query, Seller.class)
                .map(Seller::getId)
                .collectList();
    }
}
