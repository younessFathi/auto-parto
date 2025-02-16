package auto.parto.utils;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.model.Piece;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class JsonUtil {
    private final ObjectMapper objectMapper;

    public <T> Mono<T> fromJson(String pieceStr , Class<T> clazz) {
        return Mono.fromCallable(() -> objectMapper.readValue(pieceStr , clazz))
                .onErrorResume(e -> {
                    // Handle deserialization errors
                    return Mono.error(new RuntimeException("Failed to deserialize JSON: " + e.getMessage(), e));
                });
    }
}
