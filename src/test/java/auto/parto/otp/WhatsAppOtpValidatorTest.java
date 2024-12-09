package auto.parto.otp;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.model.Customer;
import auto.parto.model.OtpDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class WhatsAppOtpValidatorTest {

    @Mock
    private OtpService otpService;

    @InjectMocks
    private WhatsAppOtpValidator whatsAppOtpValidator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void validateTest() {
        CustomerDto customerDto = CustomerDto.builder()
                .phoneNumber("212445566778")
                .jwtKey("")
                .id(UUID.randomUUID())
                .email("youness@gmail.com")
                .firstName("youness")
                .lastName("fathi")
                .otpDetailsDto(
                        OtpDetailsDto.builder()
                                .otp("1234")
                                .timestamp(LocalDateTime.now())
                                .build()
                )
                .build();
        when(otpService.validate(any() , anyString())).thenReturn(Mono.just(customerDto));
        StepVerifier.create(whatsAppOtpValidator.validate(customerDto , ""))
                .expectNext(customerDto)
                .verifyComplete();

    }

}
