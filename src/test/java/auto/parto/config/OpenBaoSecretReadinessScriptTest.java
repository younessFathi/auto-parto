package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpenBaoSecretReadinessScriptTest {
    private static final Path SCRIPT = Path.of("deploy/openbao/auto-parto-check-secret-bundle");

    @TempDir
    Path tempDirectory;

    @Test
    void missingBundleBlocksApplicationStartWithoutPrintingSecretValues() throws Exception {
        CommandResult result = run(false);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("credential bundle is unavailable or incomplete")
                .doesNotContain("PRIVATE_TEST_VALUE");
    }

    @Test
    void completeBundleForCurrentGroupPassesAndAuditRequiresSeparateBundle() throws Exception {
        Path application = tempDirectory.resolve("secrets.properties");
        Files.writeString(application, completeProperties());
        secureMode(application);

        CommandResult result = run(false);
        assertThat(result.exitCode()).as(result.output()).isZero();

        CommandResult missingAudit = run(true);
        assertThat(missingAudit.exitCode()).isNotZero();

        Path auditDirectory = tempDirectory.resolve("audit");
        Files.createDirectories(auditDirectory);
        Path audit = auditDirectory.resolve("secrets.properties");
        Files.writeString(audit, "auto.parto.audit.username=audit-user\nauto.parto.audit.password=audit-password\n");
        secureMode(audit);

        assertThat(run(true).exitCode()).isZero();
    }

    @Test
    void delayedAgentBundleAllowsThePreStartGateToRecover() throws Exception {
        int group = (int) Files.getAttribute(tempDirectory, "unix:gid");
        ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString(), "--wait-seconds", "5")
                .redirectErrorStream(true);
        builder.environment().put("AUTO_PARTO_SECRET_DIR", tempDirectory.toString());
        builder.environment().put("AUTO_PARTO_AUDIT_SECRET_DIR", tempDirectory.resolve("audit").toString());
        builder.environment().put("AUTO_PARTO_SECRET_GROUP", Integer.toString(group));
        Process process = builder.start();
        Thread.sleep(250);
        Path application = tempDirectory.resolve("secrets.properties");
        Files.writeString(application, completeProperties());
        secureMode(application);

        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output).doesNotContain("PRIVATE_TEST_VALUE");
    }

    @Test
    void systemdAppUnitRequiresAgentAndRunsTheCredentialGateBeforeCompose() throws IOException {
        String unit = Files.readString(Path.of("deploy/openbao/auto-parto-app.service.example"));

        assertThat(unit).contains("Requires=docker.service openbao-agent.service")
                .contains("After=docker.service openbao-agent.service")
                .contains("ExecStartPre=/usr/local/sbin/auto-parto-check-secret-bundle --wait-seconds 120")
                .contains("PartOf=openbao-agent.service");

        String pathUnit = Files.readString(Path.of("deploy/openbao/auto-parto-secrets-changed.path.example"));
        String refreshService = Files.readString(Path.of("deploy/openbao/auto-parto-secrets-changed.service.example"));
        assertThat(pathUnit).contains("PathExists=/run/auto-parto/secrets/secrets.properties")
                .contains("PathChanged=/run/auto-parto/secrets/secrets.properties");
        assertThat(refreshService).contains("ExecStartPost=/usr/bin/systemctl restart auto-parto-app.service");
    }

    private CommandResult run(boolean auditEnabled) throws Exception {
        int group = (int) Files.getAttribute(tempDirectory, "unix:gid");
        ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString())
                .redirectErrorStream(true);
        builder.environment().put("AUTO_PARTO_SECRET_DIR", tempDirectory.toString());
        builder.environment().put("AUTO_PARTO_AUDIT_SECRET_DIR", tempDirectory.resolve("audit").toString());
        builder.environment().put("AUTO_PARTO_SECRET_GROUP", Integer.toString(group));
        builder.environment().put("APP_AUDIT_OUTBOX_ENABLED", Boolean.toString(auditEnabled));
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        return new CommandResult(process.waitFor(), output);
    }

    private static void secureMode(Path file) throws IOException {
        Files.setPosixFilePermissions(file, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.GROUP_READ));
    }

    private static String completeProperties() {
        return "spring.r2dbc.username=runtime-user\n"
                + "spring.r2dbc.password=PRIVATE_TEST_VALUE\n"
                + "spring.data.redis.password=redis-password\n"
                + "auto.parto.storage.s3.access-key=storage-access\n"
                + "auto.parto.storage.s3.secret-key=storage-secret\n"
                + "jwt.signing.algorithm=RS256\n"
                + "jwt.signing.key-id=active\n"
                + "jwt.signing.private-key=private-key\n"
                + "jwt.signing.verification-keys={\"active\":\"public-key\"}\n";
    }

    private record CommandResult(int exitCode, String output) { }
}
