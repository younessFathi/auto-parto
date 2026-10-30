package auto.parto.config;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SecretsBundleDeploymentIT {

    private static final DockerImageName TEST_IMAGE = DockerImageName.parse("alpine:3.20");
    private static final String OLD_GENERATION = "a".repeat(64);
    private static final String INCOMPLETE_GENERATION = "b".repeat(64);

    @Test
    void applicationIdentityCanReadButCannotWriteMountedBundle() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        String secretVolume = createVolume();
        try {
            try (GenericContainer<?> setup = alpineWithVolume(secretVolume, false, null, null)) {
                setup.start();
                var prepared = setup.execInContainer("sh", "-ec", "mkdir -p /run/auto-parto/secrets " +
                        "/run/auto-parto/audit; " +
                        "printf 'TEST_MARKER=nonsecret\\n' > /run/auto-parto/secrets/secrets.properties; " +
                        "printf 'TEST_AUDIT_MARKER=nonsecret\\n' > /run/auto-parto/audit/secrets.properties; " +
                        "chown 0:10001 /run/auto-parto/secrets /run/auto-parto/audit " +
                        "/run/auto-parto/secrets/secrets.properties /run/auto-parto/audit/secrets.properties; " +
                        "chmod 0750 /run/auto-parto/secrets /run/auto-parto/audit; " +
                        "chmod 0440 /run/auto-parto/secrets/secrets.properties " +
                        "/run/auto-parto/audit/secrets.properties");
                assertThat(prepared.getExitCode()).as(prepared.getStderr()).isZero();
            }

            try (GenericContainer<?> application = alpineWithVolume(secretVolume, true, "10001:10001", null)) {
                application.start();
                var access = application.execInContainer("sh", "-ec",
                        "test -r /run/auto-parto/secrets/secrets.properties; " +
                                "test ! -w /run/auto-parto/secrets/secrets.properties; " +
                                "cat /run/auto-parto/secrets/secrets.properties >/dev/null; " +
                                "if printf x >> /run/auto-parto/secrets/secrets.properties 2>/dev/null; " +
                                "then exit 9; fi");
                assertThat(access.getExitCode()).as(access.getStderr()).isZero();
            }
        } finally {
            removeVolume(secretVolume);
        }
    }

    @Test
    void incompleteLaterBundleCannotSwitchTheActiveGeneration() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        String secretVolume = createVolume();
        Path activationScript = Path.of("deploy/ansible/activate-secret-generation.sh").toAbsolutePath();
        try (GenericContainer<?> host = alpineWithVolume(secretVolume, false, null, activationScript)) {
            host.start();
            var prepared = host.execInContainer("sh", "-ec",
                    "mkdir -p /run/auto-parto/generations/" + OLD_GENERATION + "/secrets " +
                            "/run/auto-parto/generations/" + INCOMPLETE_GENERATION + "/secrets; " +
                            "printf 'ACTIVE=old\\n' > /run/auto-parto/generations/" + OLD_GENERATION +
                            "/secrets/secrets.properties; " +
                            "printf 'STAGED=new\\n' > /run/auto-parto/generations/" + INCOMPLETE_GENERATION +
                            "/secrets/secrets.properties; " +
                            "ln -s generations/" + OLD_GENERATION + " /run/auto-parto/active");
            assertThat(prepared.getExitCode()).as(prepared.getStderr()).isZero();

            var activation = host.execInContainer("sh", "-ec",
                    "if sh /activate-secret-generation.sh /run/auto-parto " + INCOMPLETE_GENERATION +
                            " secrets/secrets.properties audit/secrets.properties; then exit 9; fi; " +
                            "test \"$(readlink /run/auto-parto/active)\" = generations/" + OLD_GENERATION + "; " +
                            "grep -q 'ACTIVE=old' /run/auto-parto/active/secrets/secrets.properties");
            assertThat(activation.getExitCode()).as(activation.getStderr()).isZero();

            var rejectedPath = host.execInContainer("sh", "-ec",
                    "if sh /activate-secret-generation.sh /run/auto-parto " + OLD_GENERATION +
                            " secrets/secrets.properties ../../etc/passwd; then exit 9; fi; " +
                            "test \"$(readlink /run/auto-parto/active)\" = generations/" + OLD_GENERATION);
            assertThat(rejectedPath.getExitCode()).as(rejectedPath.getStderr()).isZero();
        } finally {
            removeVolume(secretVolume);
        }
    }

    private static GenericContainer<?> alpineWithVolume(
            String volumeName, boolean readOnly, String user, Path activationScript) {
        return new GenericContainer<>(TEST_IMAGE)
                .withCommand("sh", "-c", "sleep 120")
                .withCreateContainerCmdModifier(command -> {
                    List<Bind> binds = new ArrayList<>();
                    binds.add(new Bind(volumeName, new Volume("/run/auto-parto"),
                            readOnly ? AccessMode.ro : AccessMode.rw));
                    if (activationScript != null) {
                        binds.add(new Bind(activationScript.toString(), new Volume("/activate-secret-generation.sh"),
                                AccessMode.ro));
                    }
                    HostConfig hostConfig = HostConfig.newHostConfig().withBinds(binds);
                    command.withHostConfig(hostConfig);
                    if (user != null) {
                        command.withUser(user);
                    }
                });
    }

    private static String createVolume() {
        String volume = "auto-parto-secret-test-" + UUID.randomUUID().toString().replace('-', '_');
        DockerClientFactory.instance().client().createVolumeCmd().withName(volume).exec();
        return volume;
    }

    private static void removeVolume(String volume) {
        DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
    }
}
