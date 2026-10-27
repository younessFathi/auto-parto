package auto.parto.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.cert.CertificateFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenBaoImageDeploymentIT {

    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Path COMPOSE_FILE = ROOT.resolve("deploy/openbao/compose.production.yml");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void pinnedNormalModeImageStartsTlsBackedWithPersistentRaftVolume() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        assumeTrue(commandAvailable("openssl"), "OpenSSL is not available");

        String project = "auto-parto-openbao-it-" + UUID.randomUUID().toString().replace('-', '_');
        String volume = project + "_data";
        int apiPort = availablePort();
        int clusterPort = availablePort();
        while (clusterPort == apiPort) {
            clusterPort = availablePort();
        }
        Path temporary = Files.createTempDirectory("openbao-image-it-");
        Path tls = Files.createDirectory(temporary.resolve("tls"));
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.setPosixFilePermissions(tls, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path config = temporary.resolve("server.hcl");
        List<String> compose = List.of("docker", "compose", "--project-name", project,
                "--file", COMPOSE_FILE.toString());
        Map<String, String> environment = environmentFor(apiPort, clusterPort, config, tls, volume);

        try {
            createTemporaryCertificate(tls);
            String hcl = Files.readString(ROOT.resolve("deploy/openbao/server.hcl.example"))
                    .replace("https://openbao-secrets.internal:8200", "https://127.0.0.1:" + apiPort)
                    .replace("https://openbao-secrets.internal:8201", "https://127.0.0.1:" + clusterPort);
            Files.writeString(config, hcl);

            CommandResult createdVolume = run(List.of("docker", "volume", "create", volume),
                    Duration.ofSeconds(20), List.of(), Map.of());
            assertThat(createdVolume.exitCode()).as(createdVolume.output()).isZero();
            CommandResult started = run(compose, Duration.ofMinutes(3),
                    List.of("up", "-d", "--wait", "--wait-timeout", "90", "openbao"), environment);
            if (started.exitCode() != 0) {
                assertThat(started.exitCode()).as(started.output() + startupDiagnostics(compose, environment)).isZero();
            }
            assertThat(started.exitCode()).isZero();

            String containerId = run(compose, Duration.ofSeconds(20), List.of("ps", "-q", "openbao"), environment)
                    .output().trim();
            assertThat(containerId).isNotBlank();
            CommandResult inspected = run(List.of("docker", "inspect", "--format", "{{json .State.Health}}", containerId),
                    Duration.ofSeconds(20), List.of(), Map.of());
            assertThat(inspected.exitCode()).as(inspected.output()).isZero();
            assertThat(inspected.output()).contains("\"Status\":\"healthy\"");

            CommandResult volumeInspect = run(List.of("docker", "volume", "inspect", volume),
                    Duration.ofSeconds(20), List.of(), Map.of());
            assertThat(volumeInspect.exitCode()).as(volumeInspect.output()).isZero();

            HttpClient client = tlsClient(tls.resolve("ca.crt"));
            Initialization initialization = initialize(client, apiPort);
            configureDisposableAppRole(client, apiPort, initialization.rootToken());
            writeStagingValue(client, apiPort, initialization.rootToken());
            SecretIdentity firstIdentity = createSecretId(client, apiPort, initialization.rootToken());
            String firstWorkloadToken = login(client, apiPort, firstIdentity);

            HttpResponse<String> allowed = request(client, apiPort, "GET",
                    "/kv/data/staging/application/runtime", null, firstWorkloadToken);
            assertThat(allowed.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(allowed.body()).path("data").path("data").path("value").asText())
                    .isEqualTo("ephemeral-image-integration-value");

            HttpResponse<String> denied = request(client, apiPort, "GET",
                    "/kv/data/production/application/runtime", null, firstWorkloadToken);
            assertThat(denied.statusCode()).isEqualTo(403);

            SecretIdentity secondIdentity = createSecretId(client, apiPort, initialization.rootToken());
            destroySecretId(client, apiPort, initialization.rootToken(), firstIdentity.accessor());
            HttpResponse<String> rejectedOldIdentity = loginAttempt(client, apiPort, firstIdentity);
            assertThat(rejectedOldIdentity.statusCode()).isNotEqualTo(200);
            String secondWorkloadToken = login(client, apiPort, secondIdentity);
            HttpResponse<String> secondIdentityAccess = request(client, apiPort, "GET",
                    "/kv/data/staging/application/runtime", null, secondWorkloadToken);
            assertThat(secondIdentityAccess.statusCode()).isEqualTo(200);

            HttpResponse<String> renewed = request(client, apiPort, "POST", "/auth/token/renew-self", Map.of(),
                    firstWorkloadToken);
            assertThat(renewed.statusCode()).isEqualTo(200);
            HttpResponse<String> revoked = request(client, apiPort, "POST", "/auth/token/revoke-self", Map.of(),
                    firstWorkloadToken);
            assertThat(revoked.statusCode()).isBetween(200, 299);
            HttpResponse<String> deniedAfterRevocation = request(client, apiPort, "GET",
                    "/kv/data/staging/application/runtime", null, firstWorkloadToken);
            assertThat(deniedAfterRevocation.statusCode()).isEqualTo(403);
            assertSuccessful(request(client, apiPort, "POST", "/auth/token/revoke-self", Map.of(), secondWorkloadToken));
            destroySecretId(client, apiPort, initialization.rootToken(), secondIdentity.accessor());
            SecretIdentity recoveryIdentity = createSecretId(client, apiPort, initialization.rootToken());
            String recoveryToken = login(client, apiPort, recoveryIdentity);

            CommandResult restarted = run(compose, Duration.ofSeconds(45), List.of("restart", "openbao"), environment);
            assertThat(restarted.exitCode()).as(restarted.output()).isZero();
            CommandResult healthyAfterRestart = run(compose, Duration.ofSeconds(90),
                    List.of("up", "-d", "--wait", "--wait-timeout", "60", "openbao"), environment);
            assertThat(healthyAfterRestart.exitCode()).as(healthyAfterRestart.output()).isZero();
            unseal(client, apiPort, initialization.unsealKey());
            HttpResponse<String> recovered = request(client, apiPort, "GET",
                    "/kv/data/staging/application/runtime", null, recoveryToken);
            assertThat(recovered.statusCode()).isEqualTo(200);
            assertSuccessful(request(client, apiPort, "POST", "/auth/token/revoke-self", Map.of(), recoveryToken));
            destroySecretId(client, apiPort, initialization.rootToken(), recoveryIdentity.accessor());
        } finally {
            run(compose, Duration.ofSeconds(45), List.of("down"), environmentFor(apiPort, clusterPort, config, tls, volume));
            run(List.of("docker", "volume", "rm", "--force", volume), Duration.ofSeconds(20), List.of(), Map.of());
            deleteTree(temporary);
        }
    }

    private static CommandResult run(List<String> base, Duration timeout, List<String> arguments,
                                    Map<String, String> environment) throws Exception {
        var command = new java.util.ArrayList<>(base);
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).directory(ROOT.toFile()).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (var input = process.getInputStream()) {
                input.transferTo(output);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read OpenBao Compose output", exception);
            }
        });
        reader.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Command timed out: " + String.join(" ", command));
        }
        reader.join();
        return new CommandResult(process.exitValue(), output.toString());
    }

    private static void createTemporaryCertificate(Path tls) throws Exception {
        Process process = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                "-days", "1", "-keyout", tls.resolve("server.key").toString(),
                "-out", tls.resolve("server.crt").toString(), "-subj", "/CN=127.0.0.1",
                "-addext", "subjectAltName=IP:127.0.0.1")
                .redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var input = process.getInputStream()) {
            input.transferTo(output);
        }
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output.toString()).isZero();
        Files.copy(tls.resolve("server.crt"), tls.resolve("ca.crt"));
        // The official image runs as the unprivileged openbao user, unlike the host user creating this fixture.
        Files.setPosixFilePermissions(tls.resolve("server.key"), PosixFilePermissions.fromString("r--r--r--"));
    }

    private static String startupDiagnostics(List<String> compose, Map<String, String> environment) {
        try {
            CommandResult logs = run(compose, Duration.ofSeconds(20), List.of("logs", "--no-color", "openbao"), environment);
            String containerId = run(compose, Duration.ofSeconds(20), List.of("ps", "-q", "openbao"), environment)
                    .output().trim();
            String healthDetails = containerId.isBlank() ? "" : run(List.of("docker", "inspect", "--format",
                    "{{json .State.Health}}", containerId),
                    Duration.ofSeconds(20), List.of(), Map.of()).output();
            return "\nOpenBao logs:\n" + logs.output() + "\nHealth state:\n" + healthDetails;
        } catch (Exception diagnosticFailure) {
            return "\nUnable to collect OpenBao startup diagnostics: " + diagnosticFailure.getClass().getSimpleName();
        }
    }

    private static HttpClient tlsClient(Path caCertificate) throws Exception {
        var certificate = CertificateFactory.getInstance("X.509");
        var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        try (InputStream input = Files.newInputStream(caCertificate)) {
            trustStore.setCertificateEntry("openbao-test-ca", certificate.generateCertificate(input));
        }
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers.getTrustManagers(), new SecureRandom());
        return HttpClient.newBuilder().sslContext(sslContext).connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    private static Initialization initialize(HttpClient client, int port) throws Exception {
        HttpResponse<String> initialized = request(client, port, "PUT", "/sys/init",
                Map.of("secret_shares", 1, "secret_threshold", 1), null);
        assertThat(initialized.statusCode()).isEqualTo(200);
        JsonNode initialization = JSON.readTree(initialized.body());
        String unsealKey = firstNonEmpty(initialization.path("keys_base64"), initialization.path("keys"));
        String rootToken = initialization.path("root_token").asText();
        assertThat(unsealKey).isNotBlank();
        assertThat(rootToken).isNotBlank();
        unseal(client, port, unsealKey);
        return new Initialization(unsealKey, rootToken);
    }

    private static void unseal(HttpClient client, int port, String key) throws Exception {
        HttpResponse<String> response = request(client, port, "PUT", "/sys/unseal", Map.of("key", key), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).path("sealed").asBoolean()).isFalse();
    }

    private static void configureDisposableAppRole(HttpClient client, int port, String rootToken) throws Exception {
        assertSuccessful(request(client, port, "PUT", "/sys/auth/approle", Map.of("type", "approle"), rootToken));
        assertSuccessful(request(client, port, "POST", "/sys/mounts/kv", Map.of("type", "kv-v2"), rootToken));
        String policy = "path \"kv/data/staging/application/*\" { capabilities = [\"read\"] }";
        assertSuccessful(request(client, port, "PUT", "/sys/policies/acl/it-staging-app",
                Map.of("policy", policy), rootToken));
        assertSuccessful(request(client, port, "POST", "/auth/approle/role/it-staging-app", Map.of(
                "token_policies", List.of("it-staging-app"),
                "token_ttl", "1h",
                "token_max_ttl", "1h",
                "secret_id_ttl", "10m",
                "secret_id_num_uses", 0,
                "bind_secret_id", true), rootToken));
    }

    private static void writeStagingValue(HttpClient client, int port, String rootToken) throws Exception {
        assertSuccessful(request(client, port, "POST", "/kv/data/staging/application/runtime",
                Map.of("data", Map.of("value", "ephemeral-image-integration-value")), rootToken));
    }

    private static SecretIdentity createSecretId(HttpClient client, int port, String rootToken) throws Exception {
        HttpResponse<String> role = request(client, port, "GET", "/auth/approle/role/it-staging-app/role-id",
                null, rootToken);
        assertSuccessful(role);
        String roleId = JSON.readTree(role.body()).path("data").path("role_id").asText();
        HttpResponse<String> secret = request(client, port, "POST",
                "/auth/approle/role/it-staging-app/secret-id", Map.of(), rootToken);
        assertSuccessful(secret);
        JsonNode secretData = JSON.readTree(secret.body()).path("data");
        return new SecretIdentity(roleId, secretData.path("secret_id").asText(),
                secretData.path("secret_id_accessor").asText());
    }

    private static String login(HttpClient client, int port, SecretIdentity identity) throws Exception {
        HttpResponse<String> response = loginAttempt(client, port, identity);
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).path("auth").path("client_token").asText();
    }

    private static HttpResponse<String> loginAttempt(HttpClient client, int port, SecretIdentity identity)
            throws Exception {
        return request(client, port, "POST", "/auth/approle/login",
                Map.of("role_id", identity.roleId(), "secret_id", identity.secretId()), null);
    }

    private static void destroySecretId(HttpClient client, int port, String rootToken, String accessor)
            throws Exception {
        assertSuccessful(request(client, port, "POST",
                "/auth/approle/role/it-staging-app/secret-id-accessor/destroy",
                Map.of("secret_id_accessor", accessor), rootToken));
    }

    private static HttpResponse<String> request(HttpClient client, int port, String method, String path,
                                                Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/v1" + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            builder.header("X-Vault-Token", token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertSuccessful(HttpResponse<String> response) {
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    private static String firstNonEmpty(JsonNode first, JsonNode second) {
        String value = first.path(0).asText();
        return value.isBlank() ? second.path(0).asText() : value;
    }

    private record Initialization(String unsealKey, String rootToken) {
    }

    private record SecretIdentity(String roleId, String secretId, String accessor) {
    }

    private static boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "version").redirectErrorStream(true).start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static Map<String, String> environmentFor(int apiPort, int clusterPort, Path config, Path tls, String volume) {
        return Map.of(
                "OPENBAO_PRIVATE_BIND_IP", "127.0.0.1",
                "OPENBAO_API_PUBLISHED_PORT", Integer.toString(apiPort),
                "OPENBAO_CLUSTER_PUBLISHED_PORT", Integer.toString(clusterPort),
                "OPENBAO_TLS_SERVER_NAME", "127.0.0.1",
                "OPENBAO_SERVER_CONFIG", config.toString(),
                "OPENBAO_TLS_DIR", tls.toString(),
                "OPENBAO_DATA_VOLUME", volume);
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
