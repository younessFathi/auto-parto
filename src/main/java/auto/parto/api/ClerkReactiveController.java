package auto.parto.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/clerk")
public class ClerkReactiveController {

    @GetMapping("/hello")
    public Mono<String> hello(ServerWebExchange exchange) {
        String userId = (String) exchange.getAttribute("userId");
        if (userId == null) {
            return Mono.just("Hello Guest 👋");
        }
        return Mono.just("Hello Clerk user: " + userId + " 👤");
    }
}
