package auto.parto.repository.mongo_repository;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.Buyer;
import auto.parto.model.File;
import auto.parto.model.NotificationPreferences;
import auto.parto.model.Seller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import reactor.test.StepVerifier;

class MongoIndexesIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    @Test
    void buyerCollectionHasIndexesOnCommonQueryFields() {
        StepVerifier.create(reactiveMongoTemplate.indexOps(Buyer.class).getIndexInfo().collectList())
                .assertNext(indexes -> {
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("email"));
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("phoneNumber"));
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("clerkId"));
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("buyer_email_active_idx"));
                })
                .verifyComplete();
    }

    @Test
    void sellerCollectionHasIndexesOnCommonQueryFields() {
        StepVerifier.create(reactiveMongoTemplate.indexOps(Seller.class).getIndexInfo().collectList())
                .assertNext(indexes -> {
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("email"));
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("seller_email_active_idx"));
                })
                .verifyComplete();
    }

    @Test
    void fileCollectionHasIndexOnPieceId() {
        StepVerifier.create(reactiveMongoTemplate.indexOps(File.class).getIndexInfo().collectList())
                .assertNext(indexes -> {
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("pieceId"));
                })
                .verifyComplete();
    }

    @Test
    void notificationPreferencesCollectionHasIndexOnUserId() {
        StepVerifier.create(reactiveMongoTemplate.indexOps(NotificationPreferences.class).getIndexInfo().collectList())
                .assertNext(indexes -> {
                    org.assertj.core.api.Assertions.assertThat(indexes)
                            .anyMatch(i -> i.getName().equals("userId"));
                })
                .verifyComplete();
    }
}
