package auto.parto.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PieceServiceCachingTest {

    @Test
    void createEvictsPieceListAndDetailsCaches() throws NoSuchMethodException {
        Method createMethod = PieceService.class.getMethod("create", auto.parto.dto.PieceDto.class);
        Caching caching = createMethod.getAnnotation(Caching.class);

        assertThat(caching).isNotNull();
        assertThat(caching.evict()).hasSize(2);
        assertThat(Arrays.stream(caching.evict()).map(CacheEvict::value).flatMap(Arrays::stream))
                .containsExactlyInAnyOrder("pieces", "pieceDetails");
        assertThat(Arrays.stream(caching.evict())
                .filter(evict -> "pieces".equals(evict.value()[0]))
                .findFirst()
                .orElseThrow()
                .allEntries()).isTrue();
        assertThat(Arrays.stream(caching.evict())
                .filter(evict -> "pieceDetails".equals(evict.value()[0]))
                .findFirst()
                .orElseThrow()
                .allEntries()).isTrue();
    }

}
