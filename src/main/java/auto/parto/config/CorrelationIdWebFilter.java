package auto.parto.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdWebFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
