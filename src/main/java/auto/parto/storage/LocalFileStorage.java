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
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.List;
import java.util.stream.Stream;

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
    public Mono<Boolean> exists(String objectKey) {
        return Mono.fromCallable(() -> Files.exists(safePath(objectKey)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String objectKey) {
        return Mono.fromCallable(() -> {
            Files.deleteIfExists(safePath(objectKey));
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<String> listObjectKeys(int limit) {
        if (limit < 1 || limit > 1000) {
            return Flux.error(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        return scanObjectKeys(limit).flatMapMany(scan -> Flux.fromIterable(scan.keys()));
    }

    @Override
    public Mono<ObjectKeyScan> scanObjectKeys(int limit) {
        if (limit < 1 || limit > 1000) {
            return Mono.error(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        return Mono.fromCallable(() -> {
            if (!Files.exists(root)) return new ObjectKeyScan(java.util.List.of(), false);
            try (Stream<Path> paths = Files.walk(root)) {
                PriorityQueue<String> smallestKeys = new PriorityQueue<>(limit + 1, Comparator.reverseOrder());
                paths.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .map(Path::toString)
                        .map(key -> key.replace(java.io.File.separatorChar, '/'))
                        .filter(key -> !key.startsWith(".staging/"))
                        .forEach(key -> retainSmallest(smallestKeys, key, limit + 1));
                List<String> keys = smallestKeys.stream().sorted().toList();
                return new ObjectKeyScan(keys.stream().limit(limit).toList(), keys.size() > limit);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static void retainSmallest(PriorityQueue<String> keys, String candidate, int capacity) {
        if (keys.size() < capacity) {
            keys.offer(candidate);
        } else if (candidate.compareTo(keys.peek()) < 0) {
            keys.poll();
            keys.offer(candidate);
        }
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
