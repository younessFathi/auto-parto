package auto.parto.mapper;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.model.Customer;
import auto.parto.model.OtpDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CustomerDbMapperTest {
    private CustomerDbMapper customerDbMapper;
    private Customer customer;
    private CustomerDto customerDto;
    private OtpRequestDto otpRequestDto;

    @BeforeEach
    public void setUp() {
        customerDbMapper = new CustomerDbMapperImpl(new OtpDbMapperImpl());
        customer = Customer
                .builder()
                    .isActive(true)
                    .createAt(LocalDateTime.now())
                    .modifiedAt(LocalDateTime.now())
                    .otpDetails(OtpDetails.builder()
                            .otp("123456")
                            .timestamp(LocalDateTime.now())
                            .build())
                    .id(UUID.randomUUID().toString())
                    .firstName("John")
                    .lastName("Doe")
                    .email("youness@gmail.com")
                    .phoneNumber("1234567890")
                .build();
        customerDto = CustomerDto
                .builder()
                    .isActive(true)
                    .createAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .otpDetailsDto(OtpDetailsDto.builder()
                            .otp("123456")
                            .timestamp(LocalDateTime.now())
                            .build())
                    .id(UUID.randomUUID().toString())
                    .firstName("Jane")
                    .lastName("Doe")
                    .email("youness@gmail.com")
                    .phoneNumber("1234567890")
                .build();
        otpRequestDto = OtpRequestDto
                                .builder()
                                .firstName("John")
                                .lastName("Doe")
                                .phoneNumber("1234567890")
                                .email("youness@gmail.com")
                                .build();
    }

    @Test
    public void testToDto() {
        CustomerDto dto = customerDbMapper.toDto(customer);
        assertEquals("John", dto.getFirstName());
        assertNotNull(dto.getOtpDetailsDto());
        assertEquals("123456" , dto.getOtpDetailsDto().getOtp());
    }

    @Test
    public void testToEntity() {
        Customer customer = customerDbMapper.toEntity(customerDto);
        assertEquals("Jane", customer.getFirstName());
        assertEquals("1234567890", customer.getPhoneNumber());
        assertNotNull(customer.getOtpDetails());
        assertEquals("123456", customer.getOtpDetails().getOtp());
    }

    @Test
    public void testToEntityFromOtpRequestDto(){
        Customer customer1 = customerDbMapper.toEntity(otpRequestDto, "123456");
        assertEquals("John", customer1.getFirstName());
        assertEquals("Doe", customer1.getLastName());
        assertEquals("1234567890", customer1.getPhoneNumber());
        assertNotNull(customer1.getOtpDetails());
        assertEquals("123456", customer1.getOtpDetails().getOtp());
    }
}
