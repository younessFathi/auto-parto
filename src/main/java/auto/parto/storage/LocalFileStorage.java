package auto.parto.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnProperty(prefix = "auto.parto.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public final class LocalFileStorage implements FileStorage {

    private final Path root;
    private final String activeProfiles;

    public LocalFileStorage(@Value("${auto.parto.storage.local.root:${auto.parto.images.uploads:../uploads}}") String root,
                            @Value("${spring.profiles.active:local}") String activeProfiles) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.activeProfiles = activeProfiles;
    }

    @PostConstruct
    void rejectProductionLocalStorage() {
        if (activeProfiles.lines().flatMap(profile -> java.util.Arrays.stream(profile.split(",")))
                .map(String::trim).anyMatch("prod"::equalsIgnoreCase)) {
            throw new IllegalStateException("Local file storage cannot be used with the prod profile");
        }
    }

    @Override
    public Mono<StoredObject> put(String objectKey, Path stagedFile, String contentType,
                                  long byteSize, String checksum) {
        Path destination = safePath(objectKey);
        return Mono.fromCallable(() -> {
            Files.createDirectories(destination.getParent());
            Files.move(stagedFile, destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject(objectKey, byteSize, checksum);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<DataBuffer> read(String objectKey) {
        Path source = safePath(objectKey);
        return DataBufferUtils.read(source, new DefaultDataBufferFactory(), 8192);
    }

    @Override
    public Mono<Void> delete(String objectKey) {
        return Mono.fromCallable(() -> {
            Files.deleteIfExists(safePath(objectKey));
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Path safePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("\\")
                || objectKey.startsWith("/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("unsafe object key");
        }
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("unsafe object key");
        return path;
    }
}
