package auto.parto.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class JwtTokenGenerator {
    @Value("${jwt.secretKey}")
    private String secretKey;
    @Value("${jwt.expiration}")
    private long expirationTime;

    public String generate(String phoneNumber) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        return Jwts.builder()
                .setSubject(phoneNumber)
                .setIssuedAt(now)
                .setExpiration(expiryDate) // 1 day
                .signWith(SignatureAlgorithm.HS512, secretKey)
                .compact();
    }
}
