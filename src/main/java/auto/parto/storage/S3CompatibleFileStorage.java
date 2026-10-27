package auto.parto.storage;

import auto.parto.config.ProviderCallTelemetry;
import auto.parto.config.DomainMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "auto.parto.storage", name = "provider", havingValue = "s3")
public final class S3CompatibleFileStorage implements FileStorage {

    private final S3AsyncClient client;
    private final String bucket;
    private final DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
    private final ProviderCallTelemetry telemetry;
    private final Duration operationTimeout;

    public S3CompatibleFileStorage(
            @Value("${auto.parto.storage.s3.endpoint}") URI endpoint,
            @Value("${auto.parto.storage.s3.region:us-east-1}") String region,
            @Value("${auto.parto.storage.s3.bucket}") String bucket,
            @Value("${auto.parto.storage.s3.access-key}") String accessKey,
            @Value("${auto.parto.storage.s3.secret-key}") String secretKey,
            @Value("${auto.parto.storage.s3.max-concurrency:32}") int maxConcurrency,
            @Value("${auto.parto.storage.s3.max-pending-requests:64}") int maxPendingRequests,
            @Value("${auto.parto.storage.s3.operation-timeout:30s}") Duration operationTimeout,
            ProviderCallTelemetry telemetry) {
        this.bucket = requireText(bucket, "bucket");
        requireText(accessKey, "accessKey");
        requireText(secretKey, "secretKey");
        if (maxConcurrency < 1 || maxConcurrency > 256) {
            throw new IllegalArgumentException("maxConcurrency must be between 1 and 256");
        }
        if (maxPendingRequests < 0 || maxPendingRequests > 1024) {
            throw new IllegalArgumentException("maxPendingRequests must be between 0 and 1024");
        }
        if (operationTimeout == null || operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
        this.telemetry = telemetry;
        this.operationTimeout = operationTimeout;
        this.client = S3AsyncClient.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(maxConcurrency)
                        .maxPendingConnectionAcquires(maxPendingRequests)
                        .connectionAcquisitionTimeout(operationTimeout)
                        .connectionTimeout(operationTimeout)
                        .readTimeout(operationTimeout)
                        .writeTimeout(operationTimeout))
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(operationTimeout)
                        .apiCallAttemptTimeout(operationTimeout))
                .build();
    }

    @Override
    public Mono<StoredObject> put(String objectKey, Path stagedFile, String contentType,
                                  long byteSize, String checksum) {
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(requireText(objectKey, "objectKey"))
                .contentType(contentType).contentLength(byteSize).metadata(java.util.Map.of("sha256", checksum)).build();
        return DomainMetrics.storageCall("put", telemetry.observe("s3", "put_object", Mono.fromFuture(client.putObject(request,
                        software.amazon.awssdk.core.async.AsyncRequestBody.fromFile(stagedFile)))
                .map(ignored -> new StoredObject(objectKey, byteSize, checksum)), operationTimeout))
                .doOnSuccess(ignored -> DomainMetrics.storageUploadBytes(byteSize));
    }

    @Override
    public Flux<DataBuffer> read(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(requireText(objectKey, "objectKey")).build();
        CompletableFuture<software.amazon.awssdk.core.async.ResponsePublisher<software.amazon.awssdk.services.s3.model.GetObjectResponse>> future =
                client.getObject(request, AsyncResponseTransformer.toPublisher());
        return DomainMetrics.storageCall("read", telemetry.observe("s3", "read_object", Mono.fromFuture(future)
                .flatMapMany(publisher -> Flux.from(publisher)
                        .map(bytes -> buffers.wrap(bytes.asReadOnlyBuffer()))), operationTimeout));
    }

    @Override
    public Mono<Boolean> exists(String objectKey) {
        HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket)
                .key(requireText(objectKey, "objectKey")).build();
        return DomainMetrics.storageCall("head", telemetry.observe("s3", "head_object", Mono.fromFuture(client.headObject(request)).thenReturn(true)
                .onErrorResume(S3Exception.class, error -> error.statusCode() == 404
                        ? Mono.just(false) : Mono.error(error)), operationTimeout));
    }

    @Override
    public Mono<Void> delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(requireText(objectKey, "objectKey")).build();
        return DomainMetrics.storageCall("delete", telemetry.observe("s3", "delete_object",
                Mono.fromFuture(client.deleteObject(request)).then(), operationTimeout));
    }

    @Override
    public Flux<String> listObjectKeys(int limit) {
        if (limit < 1 || limit > 1000) {
            return Flux.error(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        return DomainMetrics.storageCall("list", telemetry.observe("s3", "list_objects",
                listPage(null, limit, new ArrayList<>()).flatMapMany(Flux::fromIterable), operationTimeout));
    }

    @Override
    public Mono<ObjectKeyScan> scanObjectKeys(int limit) {
        if (limit < 1 || limit > 1000) {
            return Mono.error(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        int pageSize = Math.min(limit + 1, 1000);
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).maxKeys(pageSize).build();
        return DomainMetrics.storageCall("scan", telemetry.observe("s3", "scan_objects", Mono.fromFuture(client.listObjectsV2(request))
                .map(response -> {
                    List<String> keys = response.contents().stream()
                            .map(software.amazon.awssdk.services.s3.model.S3Object::key)
                            .limit(limit)
                            .toList();
                    return new ObjectKeyScan(keys, response.isTruncated() || response.contents().size() > limit);
                }), operationTimeout));
    }

    private Mono<List<String>> listPage(String continuationToken, int limit, List<String> keys) {
        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder().bucket(bucket).maxKeys(limit);
        if (continuationToken != null) builder.continuationToken(continuationToken);
        return Mono.fromFuture(client.listObjectsV2(builder.build())).flatMap(response -> {
            response.contents().stream().map(software.amazon.awssdk.services.s3.model.S3Object::key)
                    .limit(limit - keys.size()).forEach(keys::add);
            if (keys.size() >= limit || !response.isTruncated()) return Mono.just(keys);
            return listPage(response.nextContinuationToken(), limit, keys);
        });
    }

    @PreDestroy
    void close() {
        client.close();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
