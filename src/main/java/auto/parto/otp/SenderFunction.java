package auto.parto.otp;

import reactor.core.publisher.Mono;

public interface SenderFunction<T , U , R> {
    Mono<R> send(T to, U otp);
}
