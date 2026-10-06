package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.persistence.BuyerIdentityOtpRepository;
import auto.parto.persistence.BuyerIdentityRecord;
import auto.parto.mapper.BuyerIdentityMapper;
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
class PostgresqlBuyerIdentityStrategyTest {

    @Mock
    private BuyerIdentityOtpRepository repository;
    @Mock
    private JwtTokenGenerator jwtTokenGenerator;
    @Mock
    private BuyerIdentityMapper mapper;

    private BuyerIdentityStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BuyerIdentityStrategy(repository, mapper, jwtTokenGenerator);
    }

    @Test
    void createUsesExistingIdentityToReplaceOtp() {
        String email = "buyer@example.test";
        BuyerIdentityRecord identity = identity(email);
        when(repository.findByEmail(email)).thenReturn(Mono.just(identity));
        when(repository.replaceOtp(eq(email), eq("123456"), any(Instant.class)))
                .thenReturn(Mono.just(identity));
        when(mapper.toDto(identity)).thenReturn(BuyerDto.builder().email(email).build());

        StepVerifier.create(strategy.create(request(email), "123456"))
                .assertNext(result -> assertThat(((BuyerDto) result).getEmail()).isEqualTo(email))
                .verifyComplete();

        verify(repository).replaceOtp(eq(email), eq("123456"), any(Instant.class));
    }

    @Test
    void validateUsesPersistedCanonicalEmailAndPublicId() {
        String requestEmail = "BUYER@EXAMPLE.TEST";
        String canonicalEmail = "buyer@example.test";
        BuyerIdentityRecord identity = identity(canonicalEmail);
        TokenDto<BuyerDto> token = TokenDto.<BuyerDto>builder()
                .token("token")
                .user(BuyerDto.builder().id(identity.publicId().toString()).email(canonicalEmail).build())
                .build();
        when(repository.findByEmail(requestEmail)).thenReturn(Mono.just(identity));
        when(repository.consumeValidOtp(eq(requestEmail), eq("123456"), any(Instant.class)))
                .thenReturn(Mono.just(true));
        when(mapper.toDto(identity)).thenReturn(BuyerDto.builder()
                .id(identity.publicId().toString()).email(canonicalEmail).build());
        when(jwtTokenGenerator.encode(eq(canonicalEmail), any(BuyerDto.class), eq(CustomerType.BUYER)))
                .thenReturn(Mono.just(token));

        StepVerifier.create(strategy.validate("123456", requestEmail))
                .assertNext(result -> assertThat(result.getToken()).isEqualTo("token"))
                .verifyComplete();

        verify(jwtTokenGenerator).encode(eq(canonicalEmail), any(BuyerDto.class), eq(CustomerType.BUYER));
    }

    @Test
    void validateRecordsFailedAttemptWhenOtpIsInvalid() {
        String email = "buyer@example.test";
        BuyerIdentityRecord identity = identity(email);
        when(repository.findByEmail(email)).thenReturn(Mono.just(identity));
        when(repository.consumeValidOtp(eq(email), eq("wrong"), any(Instant.class)))
                .thenReturn(Mono.just(false));
        when(repository.recordFailedAttempt(eq(email), any(Instant.class))).thenReturn(Mono.empty());

        StepVerifier.create(strategy.validate("wrong", email))
                .expectError(OtpInvalidException.class)
                .verify();

        verify(repository).recordFailedAttempt(eq(email), any(Instant.class));
    }

    private static BuyerIdentityRecord identity(String email) {
        return new BuyerIdentityRecord(UUID.randomUUID(), email, "Ada", "Lovelace", "+212600000000",
                true, "hash", Instant.now().plusSeconds(300), 0, null);
    }

    private static OtpRequestDto request(String email) {
        return OtpRequestDto.builder()
                .email(email)
                .firstName("Ada")
                .lastName("Lovelace")
                .phoneNumber("+212600000000")
                .customerType(CustomerType.BUYER)
                .build();
    }
}
