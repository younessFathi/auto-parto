package auto.parto.config;

import auto.parto.repository.NotificationPreferencesRepository;
import auto.parto.repository.mongo_repository.BuyerRepository;
import auto.parto.repository.mongo_repository.FileRepository;
import auto.parto.repository.mongo_repository.PieceOfferRepository;
import auto.parto.repository.mongo_repository.PieceRepository;
import auto.parto.repository.mongo_repository.SearchPieceRepository;
import auto.parto.repository.mongo_repository.SellerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MongoDisabledRepositoryConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("auto.parto.persistence.postgresql.cutover.mongo-disabled=true")
            .withUserConfiguration(BuyerRepository.class, SellerRepository.class, PieceRepository.class,
                    FileRepository.class, PieceOfferRepository.class, SearchPieceRepository.class,
                    NotificationPreferencesRepository.class);

    @Test
    void doesNotCreateLegacyMongoRepositoriesDuringCutover() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(BuyerRepository.class);
            assertThat(context).doesNotHaveBean(SellerRepository.class);
            assertThat(context).doesNotHaveBean(PieceRepository.class);
            assertThat(context).doesNotHaveBean(FileRepository.class);
            assertThat(context).doesNotHaveBean(PieceOfferRepository.class);
            assertThat(context).doesNotHaveBean(SearchPieceRepository.class);
            assertThat(context).doesNotHaveBean(NotificationPreferencesRepository.class);
        });
    }
}
