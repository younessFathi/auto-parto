package auto.parto.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("google")
public class HelloController {

    @GetMapping("hello")
    public ResponseEntity<String> sayHello(){
        return ResponseEntity.ok().body("Hello World");
    }

    @GetMapping("/me")
    public Map<String, Object> me(JwtAuthenticationToken token) {
        return Map.of(
                "sub", token.getTokenAttributes().get("sub"),
                "email", token.getTokenAttributes().get("email"),
                "name", token.getTokenAttributes().get("name")
        );
    }
}
