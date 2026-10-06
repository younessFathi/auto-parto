package auto.parto.api;

import auto.parto.persistence.BuyerProfileRepository;
import auto.parto.service.BuyerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {BuyerProfileController.class, BuyerProfileService.class,
        BuyerProfileControllerConditionTest.ConditionBeans.class, TestConfig.class})
@TestPropertySource(properties = {
        "auto.parto.persistence.postgresql.enabled=false",
        "auto.parto.persistence.postgresql.buyer-identity.enabled=true"
})
class BuyerProfileControllerConditionTest {

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void buyerFlagCreatesCompleteProfileBoundaryWithoutSellerPostgresqlFlag() {
        assertThat(applicationContext.getBeansOfType(BuyerProfileController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(BuyerProfileService.class)).hasSize(1);
    }

    @TestConfiguration
    static class ConditionBeans {
        @Bean
        BuyerProfileRepository buyerProfileRepository() {
            return org.mockito.Mockito.mock(BuyerProfileRepository.class);
        }
    }
}
