package auto.parto.utils;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtTokenGeneratorTest {

    @InjectMocks
    private JwtTokenGenerator jwtTokenGenerator;

    private static final String SECRET_KEY = "H1lq8Mx5WjD3+KPxBlj+F+Qo5rFkNc1uWy2FpiGpWXM=";
    private static final long EXPIRATION_TIME = 3600000L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtTokenGenerator, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtTokenGenerator, "expirationTime", EXPIRATION_TIME);
    }

    @Test
    void encode_withUserAndCustomerType_returnsTokenWithUser() {
        BuyerDto user = BuyerDto.builder()
                .id("buyer-123")
                .email("buyer@test.com")
                .build();

        TokenDto<BuyerDto> result = jwtTokenGenerator.encode("buyer@test.com", user, CustomerType.BUYER)
                .block();

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotEmpty();
        assertThat(result.getUser()).isEqualTo(user);
    }

    @Test
    void encode_simpleValue_returnsToken() {
        TokenDto result = jwtTokenGenerator.encode("testuser@example.com")
                .block();

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotEmpty();
    }

    @Test
    void generateToken_createsValidToken() {
        String token = jwtTokenGenerator.generateToken("testuser");

        assertThat(token).isNotEmpty();
        assertThat(jwtTokenGenerator.validateToken(token)).isTrue();
    }

    @Test
    void keyGenerator_generatesConsistentKey() {
        String key1 = JwtTokenGenerator.keyGenerator("+1234567890");
        String key2 = JwtTokenGenerator.keyGenerator("+1234567890");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1).isNotEmpty();
        assertThat(Base64.getDecoder().decode(key1)).hasSize(64);
    }

    @Test
    void keyGenerator_differentInputsDifferentKeys() {
        String key1 = JwtTokenGenerator.keyGenerator("+1234567890");
        String key2 = JwtTokenGenerator.keyGenerator("+0987654321");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void validateToken_returnsTrue_forValidToken() {
        String token = jwtTokenGenerator.generateToken("validuser");

        assertThat(jwtTokenGenerator.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalse_forInvalidToken() {
        assertThat(jwtTokenGenerator.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forEmptyToken() {
        assertThat(jwtTokenGenerator.validateToken("")).isFalse();
    }

    @Test
    void extractClaim_extractsSubject() {
        String token = jwtTokenGenerator.generateToken("subjectuser");

        String subject = jwtTokenGenerator.extractClaim(token, Claims::getSubject);

        assertThat(subject).isEqualTo("subjectuser");
    }

    @Test
    void extractClaim_extractsRole() {
        String token = jwtTokenGenerator.generateToken("adminuser");

        String role = jwtTokenGenerator.extractClaim(token, claims -> claims.get("role", String.class));

        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    void extractUsername_returnsSubject() {
        String token = jwtTokenGenerator.generateToken("username");

        String username = jwtTokenGenerator.extractUsername(token);

        assertThat(username).isEqualTo("username");
    }

    @Test
    void getAuthentication_createsAuthenticationWithAuthorities() {
        String token = jwtTokenGenerator.generateToken("authuser");

        Authentication auth = jwtTokenGenerator.getAuthentication(token);

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(org.springframework.security.core.userdetails.User.class);
        assertThat(auth.getCredentials()).isEqualTo(token);
    }

    @Test
    void encode_createsTokenWithSellerRole() {
        SellerDto seller = SellerDto.builder()
                .id("seller-123")
                .email("seller@test.com")
                .build();

        TokenDto<SellerDto> result = jwtTokenGenerator.encode("seller@test.com", seller, CustomerType.SELLER)
                .block();

        assertThat(result).isNotNull();
        String extractedRole = jwtTokenGenerator.extractClaim(result.getToken(),
                claims -> claims.get("roles", String.class));
        assertThat(extractedRole).isEqualTo("ROLE_SELLER");
    }

    @Test
    void encode_createsTokenWithBuyerRole() {
        BuyerDto buyer = BuyerDto.builder()
                .id("buyer-123")
                .email("buyer@test.com")
                .build();

        TokenDto<BuyerDto> result = jwtTokenGenerator.encode("buyer@test.com", buyer, CustomerType.BUYER)
                .block();

        assertThat(result).isNotNull();
        String extractedRole = jwtTokenGenerator.extractClaim(result.getToken(),
                claims -> claims.get("roles", String.class));
        assertThat(extractedRole).isEqualTo("ROLE_BUYER");
    }
}