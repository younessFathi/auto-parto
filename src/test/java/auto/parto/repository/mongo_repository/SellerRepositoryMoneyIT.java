package auto.parto.repository.mongo_repository;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class SellerRepositoryMoneyIT extends BaseIntegrationTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @AfterEach
    void cleanUp() {
        mongoTemplate.dropCollection(Seller.class).block();
    }

    @Test
    void rejectsLongMinValueDebitBeforeMongoUpdate() {
        StepVerifier.create(sellerRepository.addFunds("seller-1", Long.MIN_VALUE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("outside the supported debit range"))
                .verify();
    }
}
