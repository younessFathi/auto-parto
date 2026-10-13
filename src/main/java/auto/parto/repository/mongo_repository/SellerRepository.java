package auto.parto.repository.mongo_repository;

import auto.parto.model.Buyer;
import auto.parto.model.Seller;
import auto.parto.repository.ISellerRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
    public Mono<Seller> updateGeneratedOtp(String email, String sentOtp, String field) {
        Query query = new Query(Criteria.where(field).is(email));

        Update update = new Update();
        if (sentOtp != null) {
            update.set("otpDetails.otp", sentOtp);
            update.set("otpDetails.timestamp", LocalDateTime.now());
        }
        return reactiveMongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Seller.class
        );
    }

    @Override
    public Mono<Seller> addFunds(String id, BigDecimal amount) {
        Query query = new Query(Criteria.where("_id").is(id).and("isActive").is(true));
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            query.addCriteria(Criteria.where("wallet.balance").gte(amount.abs()));
        }
        Update update = new Update().inc("wallet.balance", amount);
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
