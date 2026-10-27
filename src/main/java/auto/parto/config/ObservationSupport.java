package auto.parto.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Creates bounded-cardinality observations that remain in Reactor context across async work. */
public final class ObservationSupport {

    private ObservationSupport() {
    }

    public static <T> Mono<T> observe(ObservationRegistry registry, String name,
                                     Map<String, String> lowCardinality, Mono<T> source) {
        return Mono.deferContextual(context -> {
            Observation observation = create(registry, name, lowCardinality, context.getOrDefault(
                    ObservationThreadLocalAccessor.KEY, null));
            return source.contextWrite(current -> current.put(ObservationThreadLocalAccessor.KEY, observation))
                    .doOnError(observation::error)
                    .doFinally(signal -> observation.stop());
        });
    }

    public static <T> Flux<T> observe(ObservationRegistry registry, String name,
                                     Map<String, String> lowCardinality, Flux<T> source) {
        return Flux.deferContextual(context -> {
            Observation observation = create(registry, name, lowCardinality, context.getOrDefault(
                    ObservationThreadLocalAccessor.KEY, null));
            return source.contextWrite(current -> current.put(ObservationThreadLocalAccessor.KEY, observation))
                    .doOnError(observation::error)
                    .doFinally(signal -> observation.stop());
        });
    }

    private static Observation create(ObservationRegistry registry, String name,
                                      Map<String, String> lowCardinality, Observation parent) {
        Observation observation = Observation.createNotStarted(name, registry);
        if (parent != null) observation.parentObservation(parent);
        lowCardinality.forEach(observation::lowCardinalityKeyValue);
        return observation.start();
    }
}
