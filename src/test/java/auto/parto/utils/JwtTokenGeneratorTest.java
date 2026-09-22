package auto.parto.utils;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.security.CanonicalPrincipal;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        Authentication authentication = jwtTokenGenerator.getAuthentication(result.getToken());
        CanonicalPrincipal principal = (CanonicalPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo("buyer-123");
        assertThat(principal.getEmail()).isEqualTo("buyer@test.com");
        assertThat(principal.getRole()).isEqualTo("ROLE_BUYER");
    }

    @Test
    void encode_simpleValue_returnsToken() {
        TokenDto result = jwtTokenGenerator.encode("testuser@example.com", "ROLE_BUYER")
                .block();

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotEmpty();
    }

    @Test
    void generateToken_createsValidToken() {
        String token = jwtTokenGenerator.generateToken("testuser", "ROLE_BUYER");

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
        String token = jwtTokenGenerator.generateToken("validuser", "ROLE_BUYER");

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
        String token = jwtTokenGenerator.generateToken("subjectuser", "ROLE_BUYER");

        String subject = jwtTokenGenerator.extractClaim(token, Claims::getSubject);

        assertThat(subject).isEqualTo("subjectuser");
    }

    @Test
    void extractClaim_extractsRole() {
        String token = jwtTokenGenerator.generateToken("buyeruser", "ROLE_BUYER");

        String role = jwtTokenGenerator.extractClaim(token, claims -> claims.get("roles", String.class));

        assertThat(role).isEqualTo("ROLE_BUYER");
    }

    @Test
    void extractUsername_returnsSubject() {
        String token = jwtTokenGenerator.generateToken("username", "ROLE_BUYER");

        String username = jwtTokenGenerator.extractUsername(token);

        assertThat(username).isEqualTo("username");
    }

    @Test
    void getAuthentication_createsAuthenticationWithAuthorities() {
        String token = jwtTokenGenerator.generateToken("authuser", "ROLE_BUYER");

        Authentication auth = jwtTokenGenerator.getAuthentication(token);

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(CanonicalPrincipal.class);
        CanonicalPrincipal principal = (CanonicalPrincipal) auth.getPrincipal();
        assertThat(principal.getId()).isEqualTo("authuser");
        assertThat(principal.getEmail()).isEqualTo("authuser");
        assertThat(principal.getRole()).isEqualTo("ROLE_BUYER");
        assertThat(auth.getCredentials()).isEqualTo(token);
        assertThat(auth.getAuthorities()).hasSize(1);
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_BUYER"));
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

    @Test
    void generateToken_withRole_usesCorrectClaimKeyAndPrefix() {
        String token = jwtTokenGenerator.generateToken("user@test.com", "ROLE_SELLER");

        String extracted = jwtTokenGenerator.extractClaim(token,
                claims -> claims.get("roles", String.class));
        assertThat(extracted).isEqualTo("ROLE_SELLER");
    }

    @Test
    void generateToken_withoutRolePrefix_addsPrefix() {
        String token = jwtTokenGenerator.generateToken("user@test.com", "BUYER");

        String extracted = jwtTokenGenerator.extractClaim(token,
                claims -> claims.get("roles", String.class));
        assertThat(extracted).isEqualTo("ROLE_BUYER");
    }

    @Test
    void encode_withRole_returnsTokenWithCorrectRole() {
        TokenDto result = jwtTokenGenerator.encode("seller@test.com", "ROLE_SELLER").block();

        assertThat(result).isNotNull();
        String extractedRole = jwtTokenGenerator.extractClaim(result.getToken(),
                claims -> claims.get("roles", String.class));
        assertThat(extractedRole).isEqualTo("ROLE_SELLER");
    }

    @Test
    void generateToken_doesNotUseRoleClaimKey() {
        String token = jwtTokenGenerator.generateToken("user@test.com", "ROLE_BUYER");

        String roleClaim = jwtTokenGenerator.extractClaim(token,
                claims -> claims.get("role", String.class));
        assertThat(roleClaim).isNull();
    }

    @Nested
    class ConfigurationValidationTest {

        @Test
        void passesWithValidSecretKey() {
            ReflectionTestUtils.setField(jwtTokenGenerator, "secretKey", SECRET_KEY);

            jwtTokenGenerator.validateConfiguration();
        }

        @Test
        void failsWhenSecretKeyIsNull() {
            ReflectionTestUtils.setField(jwtTokenGenerator, "secretKey", null);

            assertThatThrownBy(() -> jwtTokenGenerator.validateConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret key is not configured");
        }

        @Test
        void failsWhenSecretKeyIsBlank() {
            ReflectionTestUtils.setField(jwtTokenGenerator, "secretKey", "   ");

            assertThatThrownBy(() -> jwtTokenGenerator.validateConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret key is not configured");
        }

        @Test
        void failsWhenSecretKeyIsUnresolvedPlaceholder() {
            ReflectionTestUtils.setField(jwtTokenGenerator, "secretKey", "${JWT_SECRET}");

            assertThatThrownBy(() -> jwtTokenGenerator.validateConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret key is not configured");
        }

        @Test
        void failsWhenSecretKeyIsNotValidBase64() {
            ReflectionTestUtils.setField(jwtTokenGenerator, "secretKey", "not-valid-base64-!!");

            assertThatThrownBy(() -> jwtTokenGenerator.validateConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not a valid Base64-encoded string");
        }

        @Test
        void failsWhenSecretKeyIsTooShort() {
            String base64short = Base64.getEncoder().encodeToString(new byte[16]);

            ReflectionTestUtils.setField(jwtTokenGenerator, "secretKey", base64short);

            assertThatThrownBy(() -> jwtTokenGenerator.validateConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 256 bits")
                    .hasMessageContaining("128 bits");
        }
    }
}
