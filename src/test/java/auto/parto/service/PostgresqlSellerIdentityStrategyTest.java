package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.persistence.SellerIdentityOtpRepository;
import auto.parto.persistence.SellerIdentityRecord;
import auto.parto.mapper.SellerIdentityMapper;
import auto.parto.utils.JwtTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresqlSellerIdentityStrategyTest {

    @Mock
    private SellerIdentityOtpRepository repository;
    @Mock
    private JwtTokenGenerator jwtTokenGenerator;
    @Mock
    private SellerIdentityMapper mapper;

    private SellerIdentityStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SellerIdentityStrategy(repository, mapper, jwtTokenGenerator);
    }

    @Test
    void createUsesExistingIdentityToReplaceOtp() {
        String email = "seller@example.test";
        SellerIdentityRecord identity = identity(email);
        when(repository.findByEmail(email)).thenReturn(Mono.just(identity));
        when(repository.replaceOtp(eq(email), eq("123456"), any(Instant.class)))
                .thenReturn(Mono.just(identity));
        when(mapper.toDto(identity)).thenReturn(SellerDto.builder().email(email).build());

        StepVerifier.create(strategy.create(request(email), "123456"))
                .assertNext(result -> assertThat(((SellerDto) result).getEmail()).isEqualTo(email))
                .verifyComplete();

        verify(repository).replaceOtp(eq(email), eq("123456"), any(Instant.class));
    }

    @Test
    void validateConsumesValidOtpAndCreatesToken() {
        String requestEmail = "SELLER@EXAMPLE.TEST";
        String canonicalEmail = "seller@example.test";
        SellerIdentityRecord identity = identity(canonicalEmail);
        TokenDto<SellerDto> token = TokenDto.<SellerDto>builder()
                .token("token")
                .user(SellerDto.builder().id(identity.publicId().toString()).email(canonicalEmail).build())
                .build();
        when(repository.findByEmail(requestEmail)).thenReturn(Mono.just(identity));
        when(repository.consumeValidOtp(eq(requestEmail), eq("123456"), any(Instant.class)))
                .thenReturn(Mono.just(true));
        when(mapper.toDto(identity)).thenReturn(SellerDto.builder()
                .id(identity.publicId().toString()).email(canonicalEmail).build());
        when(jwtTokenGenerator.encode(eq(canonicalEmail), any(SellerDto.class), eq(CustomerType.SELLER)))
                .thenReturn(Mono.just(token));

        StepVerifier.create(strategy.validate("123456", requestEmail))
                .assertNext(result -> assertThat(result.getToken()).isEqualTo("token"))
                .verifyComplete();

        verify(jwtTokenGenerator).encode(eq(canonicalEmail), any(SellerDto.class), eq(CustomerType.SELLER));
    }

    @Test
    void validateRecordsFailedAttemptWhenOtpIsInvalid() {
        String email = "seller@example.test";
        SellerIdentityRecord identity = identity(email);
        when(repository.findByEmail(email)).thenReturn(Mono.just(identity));
        when(repository.consumeValidOtp(eq(email), eq("wrong"), any(Instant.class)))
                .thenReturn(Mono.just(false));
        when(repository.recordFailedAttempt(eq(email), any(Instant.class))).thenReturn(Mono.empty());

        StepVerifier.create(strategy.validate("wrong", email))
                .expectError(OtpInvalidException.class)
                .verify();

        verify(repository).recordFailedAttempt(eq(email), any(Instant.class));
    }

    private static SellerIdentityRecord identity(String email) {
        return new SellerIdentityRecord(UUID.randomUUID(), email, "Ada", "Lovelace", true,
                "hash", Instant.now().plusSeconds(300), 0, null);
    }

    private static OtpRequestDto request(String email) {
        return OtpRequestDto.builder()
                .email(email)
                .firstName("Ada")
                .lastName("Lovelace")
                .customerType(CustomerType.SELLER)
                .build();
    }
}
