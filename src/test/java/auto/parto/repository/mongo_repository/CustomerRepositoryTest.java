package auto.parto.repository.mongo_repository;

import auto.parto.model.Customer;
import auto.parto.model.OtpDetails;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled
public class CustomerRepositoryTest {

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;
    @Autowired
    private CustomerRepository customerRepository;

    @BeforeAll
    void setup() {
        reactiveMongoTemplate.dropCollection(Customer.class).block();
        reactiveMongoTemplate.createCollection(Customer.class).block();
    }

    @Test
    public void findAllTest() {
        Customer customer = Customer.builder()
                .phoneNumber("212445566778")
                .jwtKey("")
                .id(UUID.randomUUID().toString())
                .email("youness@gmail.com")
                .firstName("youness")
                .lastName("fathi")
                .otpDetails(
                        OtpDetails.builder()
                                .otp("1234")
                                .timestamp(LocalDateTime.now())
                                .build()
                )
                .build();
        Customer customer2 = Customer.builder()
                .phoneNumber("212445566779")
                .jwtKey("")
                .id(UUID.randomUUID().toString())
                .email("youness@gmail.com")
                .firstName("youness")
                .lastName("fathi")
                .otpDetails(
                        OtpDetails.builder()
                                .otp("1234")
                                .timestamp(LocalDateTime.now())
                                .build()
                )
                .build();
        reactiveMongoTemplate.save(customer).block();
        reactiveMongoTemplate.save(customer2).block();

        StepVerifier.create(customerRepository.findAll())
                .expectNextMatches(savedCustomer -> savedCustomer.getId().equals(customer.getId()))
                .expectNextMatches(savedCustomer-> savedCustomer.getPhoneNumber().equals(customer2.getPhoneNumber()))
                .verifyComplete();

    }
}
