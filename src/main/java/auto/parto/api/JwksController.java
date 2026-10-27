package auto.parto.api;

import auto.parto.utils.JwtTokenGenerator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {
    private final JwtTokenGenerator jwtTokenGenerator;

    public JwksController(JwtTokenGenerator jwtTokenGenerator) { this.jwtTokenGenerator = jwtTokenGenerator; }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() { return jwtTokenGenerator.publishedJwks(); }
}
