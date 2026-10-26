package auto.parto.utils;

import auto.parto.dto.JwtKeyProvider;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.security.CanonicalPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.math.BigInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.function.Function;


@Component
@Slf4j
public class JwtTokenGenerator {
    public static final String ROLE_PREFIX = "ROLE_";
    @Value("${jwt.secretKey:}")
    private String secretKey;
    @Value("${jwt.signing.algorithm:HS256}")
    private String algorithm;
    @Value("${jwt.signing.key-id:local}")
    private String keyId;
    @Value("${jwt.signing.private-key:}")
    private String privateKeyMaterial;
    @Value("${jwt.signing.verification-keys:{}}")
    private String verificationKeysMaterial;
    @Value("${jwt.expiration}")
    private long expirationTime;
    private static final String AUTHORITIES_KEY = "roles";

    @PostConstruct
    public void validateConfiguration() {
        if ("RS256".equalsIgnoreCase(algorithm)) {
            try {
                signingPrivateKey();
                verificationKeys();
                if (!verificationKeys().containsKey(keyId)) throw new IllegalStateException("JWT signing key id must be present in verification key set");
                return;
            } catch (Exception e) {
                throw new IllegalStateException("JWT RS256 signing and verification keys are not configured correctly");
            }
        }
        if (!"HS256".equalsIgnoreCase(algorithm)) throw new IllegalStateException("JWT signing algorithm must be RS256 or HS256");
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                "JWT secret key is not configured. Set JWT_SECRET environment variable to a Base64-encoded key of at least 256 bits."
            );
        }
        if (secretKey.contains("${")) {
            throw new IllegalStateException(
                "JWT secret key is not configured. Set JWT_SECRET environment variable to a Base64-encoded key of at least 256 bits. " +
                "Unresolved placeholder: " + secretKey
            );
        }
        try {
            byte[] decodedKey = Base64.getDecoder().decode(secretKey);
            if (decodedKey.length < 32) {
                throw new IllegalStateException(
                    "JWT secret key must be at least 256 bits (32 bytes) after Base64 decoding. " +
                    "Current key is only " + (decodedKey.length * 8) + " bits."
                );
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "JWT secret key is not a valid Base64-encoded string. " +
                "Set JWT_SECRET environment variable to a Base64-encoded key of at least 256 bits."
            );
        }
    }

    public <T extends JwtKeyProvider> Mono<TokenDto<T>> encode(String value , T user , CustomerType type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        String token = Jwts.builder()
                            .subject(user.getId())
                            .claim("email", user.getEmail())
                            .claim("principal_id", user.getId())
                            .claim(AUTHORITIES_KEY , ROLE_PREFIX + type.name())
                            .header().keyId(keyId).and()
                            .signWith(signingKey(), signingAlgorithm())
                            .issuedAt(now)
                            .expiration(expiryDate)
                            .compact();
        return Mono.just(
                TokenDto.<T>builder()
                        .token(token)
                        .user(user)
                        .build()
        );
    }

    private SecretKey getSigningKey() {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(decodedKey);
    }

    public Mono<TokenDto> encode(String value, String role) {
        String token = generateToken(value, role);
        return Mono.just(
                TokenDto.builder()
                        .token(token)
                        .build()
        );
    }

    public String generateToken(String value, String role) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiryDate = new Date(nowMillis + expirationTime);
        String authorities = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
        String token = Jwts.builder()
                .subject(value)
                .claim("principal_id", value)
                .claim("email", value)
                .issuedAt(now)
                .expiration(expiryDate)
                .claim(AUTHORITIES_KEY, authorities)
                .header().keyId(keyId).and()
                .signWith(signingKey(), signingAlgorithm())
                .compact();
        return token;
    }

    public static String keyGenerator(String phoneNumber) {
        KeyGenerator keyGen = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hashedBytes = digest.digest(phoneNumber.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = new byte[64];
            System.arraycopy(hashedBytes, 0, signingKey, 0, 64);
            return Base64.getEncoder().encodeToString(signingKey);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Boolean validateToken(String token) {
        try {
            Claims claims = parser()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().after(new java.util.Date());
        } catch (Exception e) {
            log.debug("JWT validation failed");
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return parser()
                .parseSignedClaims(token)
                .getPayload();
    }

    private io.jsonwebtoken.JwtParser parser() {
        if ("RS256".equalsIgnoreCase(algorithm)) {
            return Jwts.parser().keyLocator(header -> {
                if (!"RS256".equals(header.getAlgorithm())) throw new IllegalArgumentException("Unsupported JWT algorithm");
                var key = verificationKeys().get(((io.jsonwebtoken.JwsHeader) header).getKeyId());
                if (key == null) throw new IllegalArgumentException("Unknown JWT key id");
                return key;
            }).build();
        }
        return Jwts.parser().verifyWith(getSigningKey()).build();
    }

    private Key signingKey() {
        return "RS256".equalsIgnoreCase(algorithm) ? signingPrivateKey() : getSigningKey();
    }

    private io.jsonwebtoken.SignatureAlgorithm signingAlgorithm() {
        return "RS256".equalsIgnoreCase(algorithm) ? io.jsonwebtoken.SignatureAlgorithm.RS256 : io.jsonwebtoken.SignatureAlgorithm.HS256;
    }

    private RSAPrivateKey signingPrivateKey() {
        try {
            byte[] der = Base64.getDecoder().decode(privateKeyMaterial.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) { throw new IllegalStateException("Invalid JWT private key"); }
    }

    private java.util.Map<String, RSAPublicKey> verificationKeys() {
        try {
            var values = new ObjectMapper().readValue(verificationKeysMaterial, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
            var result = new java.util.HashMap<String, RSAPublicKey>();
            var factory = KeyFactory.getInstance("RSA");
            values.forEach((kid, value) -> {
                try { result.put(kid, (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(value)))); }
                catch (Exception e) { throw new IllegalArgumentException("Invalid JWT public key"); }
            });
            return result;
        } catch (Exception e) { throw new IllegalStateException("Invalid JWT verification key set"); }
    }

    public java.util.Map<String, Object> publishedJwks() {
        var keys = verificationKeys().entrySet().stream().map(entry -> {
            RSAPublicKey key = entry.getValue();
            return java.util.Map.<String, Object>of("kty", "RSA", "use", "sig", "alg", "RS256", "kid", entry.getKey(),
                    "n", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(key.getModulus())),
                    "e", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(key.getPublicExponent())));
        }).toList();
        return java.util.Map.of("keys", keys);
    }

    private byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes.length > 1 && bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Authentication getAuthentication(String token) {
        Claims claims = extractAllClaims(token);

        Object authoritiesClaim = claims.get(AUTHORITIES_KEY);
        if (!(authoritiesClaim instanceof String authoritiesValue) || authoritiesValue.isBlank()) {
            throw new IllegalArgumentException("JWT does not contain exactly one canonical role");
        }
        if (!isAllowedRole(authoritiesValue)) {
            throw new IllegalArgumentException("JWT does not contain exactly one allowed canonical role");
        }
        Collection<? extends GrantedAuthority> authorities = AuthorityUtils
                .commaSeparatedStringToAuthorityList(authoritiesValue);
        String principalId = claims.get("principal_id", String.class);
        String email = claims.get("email", String.class);
        if (principalId == null || principalId.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("JWT does not contain canonical identity claims");
        }
        String role = authorities.iterator().next().getAuthority();
        CanonicalPrincipal principal = new CanonicalPrincipal(principalId, email, role);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    private boolean isAllowedRole(String role) {
        return "ROLE_BUYER".equals(role)
                || "ROLE_SELLER".equals(role)
                || "ROLE_ADMIN".equals(role);
    }
}
