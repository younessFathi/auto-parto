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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AnsibleVaultDeploymentIT {

    private static final DockerImageName PYTHON_IMAGE = DockerImageName.parse("python:3.12-slim-bookworm");
    private static final String APP_VAULT_ID = "app@/fixtures/app-password";
    private static final String AUDIT_VAULT_ID = "audit@/fixtures/audit-password";

    @Test
    void failedRetryWithUnchangedCiphertextPreservesActiveBundles() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        String secretVolume = createVolume("auto-parto-ansible-secrets-");
        String fixtureVolume = createVolume("auto-parto-ansible-fixtures-");
        Path playbookDirectory = Path.of("deploy/ansible").toAbsolutePath().normalize();

        try (GenericContainer<?> controller = ansibleController(
                secretVolume, fixtureVolume, playbookDirectory)) {
            controller.start();
            var installed = controller.execInContainer("sh", "-ec", """
                    apt-get update -qq
                    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq sudo
                    python3 -m venv /opt/ansible
                    /opt/ansible/bin/pip install --disable-pip-version-check --no-cache-dir ansible-core==2.17.12
                    mkdir -p /run/lock /fixtures
                    printf 'app-test-unlock-value\\n' > /fixtures/app-password
                    printf 'audit-test-unlock-value\\n' > /fixtures/audit-password
                    chmod 0600 /fixtures/app-password /fixtures/audit-password
                    printf 'APP_TEST_PROPERTY=app-readable-marker\\n' > /fixtures/application.clear
                    printf 'AUDIT_TEST_PROPERTY=audit-readable-marker\\n' > /fixtures/audit.clear
                    printf 'DB_BOOTSTRAP_TEST_PROPERTY=db-bootstrap-marker\\n' > /fixtures/database-bootstrap.clear
                    printf 'BACKUP_TEST_PROPERTY=backup-marker\\n' > /fixtures/backup.clear
                    printf 'AUDIT_ADMIN_TEST_PROPERTY=audit-admin-marker\\n' > /fixtures/audit-admin.clear
                    printf 'OBJECT_STORAGE_TEST_PROPERTY=object-storage-admin-marker\\n' > /fixtures/object-storage-admin.clear
                    printf 'MIGRATION_TEST_PROPERTY=migration-cleanup-marker\\n' > /fixtures/migration.clear
                    /opt/ansible/bin/ansible-vault encrypt --vault-id app@/fixtures/app-password --output /fixtures/application.vault /fixtures/application.clear
                    /opt/ansible/bin/ansible-vault encrypt --vault-id audit@/fixtures/audit-password --output /fixtures/audit.vault /fixtures/audit.clear
                    /opt/ansible/bin/ansible-vault encrypt --vault-id app@/fixtures/app-password --output /fixtures/database-bootstrap.vault /fixtures/database-bootstrap.clear
                    /opt/ansible/bin/ansible-vault encrypt --vault-id app@/fixtures/app-password --output /fixtures/backup.vault /fixtures/backup.clear
                    /opt/ansible/bin/ansible-vault encrypt --vault-id app@/fixtures/app-password --output /fixtures/audit-admin.vault /fixtures/audit-admin.clear
                    /opt/ansible/bin/ansible-vault encrypt --vault-id app@/fixtures/app-password --output /fixtures/object-storage-admin.vault /fixtures/object-storage-admin.clear
                    /opt/ansible/bin/ansible-vault encrypt --vault-id app@/fixtures/app-password --output /fixtures/migration.vault /fixtures/migration.clear
                    printf '%s\\n' \\
                      'all:' \\
                      '  children:' \\
                      '    secret_bundle_targets:' \\
                      '      hosts:' \\
                      '        localhost:' \\
                      '          ansible_connection: local' \\
                      '          secret_target_role: application' \\
                      '          vault_bundles:' \\
                      '            - source: /fixtures/application.vault' \\
                      '              role: application-runtime' \\
                      '              gid: 10001' \\
                      '              generation_path: secrets/secrets.properties' \\
                      '            - source: /fixtures/audit.vault' \\
                      '              role: audit-runtime' \\
                      '              gid: 10001' \\
                      '              generation_path: audit/secrets.properties' \\
                      > /fixtures/inventory.yml
                    sed '/source: \\/fixtures\\/application.vault/,/generation_path: secrets\\/secrets.properties/d' /fixtures/inventory.yml > /fixtures/audit-only.yml
                    sed -e 's/role: audit-runtime/role: application-runtime/' -e 's#generation_path: audit/secrets.properties#generation_path: secrets/secrets.properties#' /fixtures/inventory.yml > /fixtures/duplicate-destination.yml
                    sed 's/gid: 10001/gid: postgres/g' /fixtures/inventory.yml > /fixtures/invalid-gid.yml
                    printf '%s\\n' \\
                      'all:' '  children:' '    secret_bundle_targets:' '      hosts:' '        localhost:' \\
                      '          ansible_connection: local' '          secret_target_role: application_database' \\
                      '          vault_bundles:' \\
                      '            - source: /fixtures/database-bootstrap.vault' \\
                      '              role: database-bootstrap' '              gid: 10002' \\
                      '              generation_path: database/bootstrap.properties' \\
                      '            - source: /fixtures/backup.vault' '              role: backup' \\
                      '              gid: 10003' '              generation_path: backup/secrets.properties' \\
                      > /fixtures/application-database-inventory.yml
                    printf '%s\\n' \\
                      'all:' '  children:' '    secret_bundle_targets:' '      hosts:' '        localhost:' \\
                      '          ansible_connection: local' '          secret_target_role: audit_database' \\
                      '          vault_bundles:' \\
                      '            - source: /fixtures/audit-admin.vault' \\
                      '              role: audit-admin' '              gid: 10004' \\
                      '              generation_path: database/audit-admin.properties' \\
                      > /fixtures/audit-database-inventory.yml
                    printf '%s\\n' \\
                      'all:' '  children:' '    secret_bundle_targets:' '      hosts:' '        localhost:' \\
                      '          ansible_connection: local' '          secret_target_role: object_storage' \\
                      '          vault_bundles:' \\
                      '            - source: /fixtures/object-storage-admin.vault' \\
                      '              role: object-storage-admin' '              gid: 10005' \\
                      '              generation_path: object-storage/admin.properties' \\
                      > /fixtures/object-storage-inventory.yml
                    printf '%s\\n' \\
                      'all:' \\
                      '  children:' \\
                      '    secret_bundle_targets:' \\
                      '      hosts:' \\
                      '        localhost:' \\
                      '          ansible_connection: local' \\
                      '          secret_target_role: migration_runner' \\
                      '          vault_bundles:' \\
                      '            - source: /fixtures/migration.vault' \\
                      '              role: migration' \\
                      '              gid: 10002' \\
                      '              generation_path: migration/secrets.properties' \\
                      > /fixtures/migration-inventory.yml
                    """);
            assertThat(installed.getExitCode()).as(installed.getStderr()).isZero();

            var firstDeployment = runPlaybook(controller, "/fixtures/inventory.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            assertThat(firstDeployment.getExitCode()).as(firstDeployment.getStderr()).isZero();
            assertBothBundlesReadable(controller);
            String activeTarget = activeTarget(controller);
            String healthyBundleSnapshot = bundleSnapshot(controller);

            var identicalRetry = runPlaybook(controller, "/fixtures/inventory.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            assertThat(identicalRetry.getExitCode()).as(identicalRetry.getStderr()).isZero();
            assertThat(activeTarget(controller)).isEqualTo(activeTarget);
            assertThat(bundleSnapshot(controller)).isEqualTo(healthyBundleSnapshot);
            assertBothBundlesReadable(controller);

            var removeRequiredApplicationBundle = controller.execInContainer("rm", "--",
                    "/run/auto-parto/active/secrets/secrets.properties");
            assertThat(removeRequiredApplicationBundle.getExitCode())
                    .as(removeRequiredApplicationBundle.getStderr()).isZero();
            var missingApplicationRetry = runPlaybook(
                    controller, "/fixtures/inventory.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            String missingApplicationOutput = missingApplicationRetry.getStdout() + "\n" + missingApplicationRetry.getStderr();
            assertThat(missingApplicationRetry.getExitCode())
                    .as("an identical retry must reject a missing required application file").isNotZero();
            assertThat(missingApplicationOutput)
                    .contains("application-runtime", "secrets/secrets.properties")
                    .doesNotContain("app-readable-marker", "audit-readable-marker");
            assertThat(activeTarget(controller)).isEqualTo(activeTarget);
            assertAuditBundleReadable(controller);
            assertApplicationLockReleased(controller);

            var restoreApplicationBundle = controller.execInContainer("install", "-o", "root", "-g", "10001",
                    "-m", "0440", "/fixtures/application.clear",
                    "/run/auto-parto/active/secrets/secrets.properties");
            assertThat(restoreApplicationBundle.getExitCode()).as(restoreApplicationBundle.getStderr()).isZero();
            var makeApplicationBundleTooPermissive = controller.execInContainer("chmod", "0644",
                    "/run/auto-parto/active/secrets/secrets.properties");
            assertThat(makeApplicationBundleTooPermissive.getExitCode())
                    .as(makeApplicationBundleTooPermissive.getStderr()).isZero();
            String incorrectModeSnapshot = bundleSnapshot(controller);
            var incorrectModeRetry = runPlaybook(
                    controller, "/fixtures/inventory.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            String incorrectModeOutput = incorrectModeRetry.getStdout() + "\n" + incorrectModeRetry.getStderr();
            assertThat(incorrectModeRetry.getExitCode())
                    .as("an identical retry must reject an incorrectly permissioned application file").isNotZero();
            assertThat(incorrectModeOutput)
                    .contains("application-runtime", "secrets/secrets.properties")
                    .doesNotContain("app-readable-marker", "audit-readable-marker");
            assertThat(activeTarget(controller)).isEqualTo(activeTarget);
            assertThat(bundleSnapshot(controller)).isEqualTo(incorrectModeSnapshot);
            assertAuditBundleReadable(controller);
            assertApplicationLockReleased(controller);
            var restoreApplicationMode = controller.execInContainer("chmod", "0440",
                    "/run/auto-parto/active/secrets/secrets.properties");
            assertThat(restoreApplicationMode.getExitCode()).as(restoreApplicationMode.getStderr()).isZero();
            assertBothBundlesReadable(controller);

            var applicationDatabaseDeployment = runPlaybook(
                    controller, "/fixtures/application-database-inventory.yml", APP_VAULT_ID);
            assertThat(applicationDatabaseDeployment.getExitCode())
                    .as(applicationDatabaseDeployment.getStdout() + "\n" + applicationDatabaseDeployment.getStderr())
                    .isZero();
            assertRoleBundle(controller, "/run/auto-parto-db/active/database/bootstrap.properties",
                    "DB_BOOTSTRAP_TEST_PROPERTY=db-bootstrap-marker");
            assertRoleBundle(controller, "/run/auto-parto-db/active/backup/secrets.properties",
                    "BACKUP_TEST_PROPERTY=backup-marker");

            var auditDatabaseDeployment = runPlaybook(
                    controller, "/fixtures/audit-database-inventory.yml", APP_VAULT_ID);
            assertThat(auditDatabaseDeployment.getExitCode())
                    .as(auditDatabaseDeployment.getStdout() + "\n" + auditDatabaseDeployment.getStderr())
                    .isZero();
            assertRoleBundle(controller, "/run/auto-parto-audit/active/database/audit-admin.properties",
                    "AUDIT_ADMIN_TEST_PROPERTY=audit-admin-marker");

            var objectStorageDeployment = runPlaybook(
                    controller, "/fixtures/object-storage-inventory.yml", APP_VAULT_ID);
            assertThat(objectStorageDeployment.getExitCode())
                    .as(objectStorageDeployment.getStdout() + "\n" + objectStorageDeployment.getStderr())
                    .isZero();
            assertRoleBundle(controller,
                    "/run/auto-parto-object-storage/active/object-storage/admin.properties",
                    "OBJECT_STORAGE_TEST_PROPERTY=object-storage-admin-marker");
            assertBothBundlesReadable(controller);

            var auditOnly = runPlaybook(controller, "/fixtures/audit-only.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            assertThat(auditOnly.getExitCode()).as("an audit-only app inventory must fail preflight").isNotZero();
            var duplicateDestination = runPlaybook(
                    controller, "/fixtures/duplicate-destination.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            assertThat(duplicateDestination.getExitCode())
                    .as("duplicate role destinations must fail preflight").isNotZero();
            var invalidGid = runPlaybook(controller, "/fixtures/invalid-gid.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            assertThat(invalidGid.getExitCode()).as("nonnumeric service GIDs must fail preflight").isNotZero();
            assertBothBundlesReadable(controller);

            var migrationDeployment = runPlaybook(controller, "/fixtures/migration-inventory.yml", APP_VAULT_ID);
            assertThat(migrationDeployment.getExitCode()).as(migrationDeployment.getStderr()).isZero();
            var migrationProperties = controller.execInContainer("sh", "-ec",
                    "grep -q '^MIGRATION_TEST_PROPERTY=migration-cleanup-marker$' " +
                            "/run/auto-parto-migration/active/migration/secrets.properties");
            assertThat(migrationProperties.getExitCode()).as(migrationProperties.getStderr()).isZero();
            var migrationCleanup = controller.execInContainer("/opt/ansible/bin/ansible-playbook",
                    "--inventory", "/fixtures/migration-inventory.yml",
                    "/playbooks/remove-migration-bundle.yml");
            assertThat(migrationCleanup.getExitCode())
                    .as(migrationCleanup.getStdout() + "\n" + migrationCleanup.getStderr()).isZero();
            var migrationRemoved = controller.execInContainer("sh", "-ec",
                    "test ! -e /run/auto-parto-migration");
            assertThat(migrationRemoved.getExitCode()).as(migrationRemoved.getStderr()).isZero();
            assertBothBundlesReadable(controller);

            var lockCreated = controller.execInContainer("mkdir", "--",
                    "/run/lock/auto-parto-secret-delivery-application");
            assertThat(lockCreated.getExitCode()).as(lockCreated.getStderr()).isZero();
            var concurrentDeployment = runPlaybook(
                    controller, "/fixtures/inventory.yml", APP_VAULT_ID, AUDIT_VAULT_ID);
            assertThat(concurrentDeployment.getExitCode()).as("an existing root lock must reject another run").isNotZero();
            var lockReleasedByTest = controller.execInContainer("rmdir", "--",
                    "/run/lock/auto-parto-secret-delivery-application");
            assertThat(lockReleasedByTest.getExitCode()).as(lockReleasedByTest.getStderr()).isZero();
            assertBothBundlesReadable(controller);

            var forceEquivalentAbsoluteActiveLink = controller.execInContainer("sh", "-ec", """
                    active_target=$(readlink -f /run/auto-parto/active)
                    ln -s "$active_target" /run/auto-parto/active.absolute
                    mv -Tf /run/auto-parto/active.absolute /run/auto-parto/active
                    """);
            assertThat(forceEquivalentAbsoluteActiveLink.getExitCode())
                    .as(forceEquivalentAbsoluteActiveLink.getStderr()).isZero();

            var failedRetry = runPlaybook(controller, "/fixtures/inventory.yml", APP_VAULT_ID);
            assertThat(failedRetry.getExitCode()).as("the audit ciphertext must fail without its vault ID")
                    .isNotZero();
            assertBothBundlesReadable(controller);

            var lockWasReleased = controller.execInContainer("sh", "-ec",
                    "test ! -e /run/lock/auto-parto-secret-delivery-application");
            assertThat(lockWasReleased.getExitCode()).as(lockWasReleased.getStderr()).isZero();
        } finally {
            removeVolume(fixtureVolume);
            removeVolume(secretVolume);
        }
    }

    private static GenericContainer<?> ansibleController(
            String secretVolume, String fixtureVolume, Path playbookDirectory) {
        return new GenericContainer<>(PYTHON_IMAGE)
                .withEnv("PATH", "/opt/ansible/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                .withEnv("ANSIBLE_CONFIG", "/playbooks/ansible.cfg")
                .withCommand("sh", "-c", "sleep 1800")
                .withCreateContainerCmdModifier(command -> command.withHostConfig(HostConfig.newHostConfig()
                        .withBinds(List.of(
                                new Bind(secretVolume, new Volume("/run/auto-parto"), AccessMode.rw),
                                new Bind(fixtureVolume, new Volume("/fixtures"), AccessMode.rw),
                                new Bind(playbookDirectory.toString(), new Volume("/playbooks"), AccessMode.ro)))));
    }

    private static org.testcontainers.containers.Container.ExecResult runPlaybook(
            GenericContainer<?> controller, String inventoryPath, String... vaultIds) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of(
                "/opt/ansible/bin/ansible-playbook",
                "--inventory", inventoryPath,
                "/playbooks/deploy-secret-bundles.yml"));
        for (String vaultId : vaultIds) {
            command.add("--vault-id");
            command.add(vaultId);
        }
        return controller.execInContainer(command.toArray(String[]::new));
    }

    private static void assertBothBundlesReadable(GenericContainer<?> controller) throws Exception {
        var result = controller.execInContainer("sh", "-ec", """
                test -r /run/auto-parto/active/secrets/secrets.properties
                test -r /run/auto-parto/active/audit/secrets.properties
                grep -q '^APP_TEST_PROPERTY=app-readable-marker$' /run/auto-parto/active/secrets/secrets.properties
                grep -q '^AUDIT_TEST_PROPERTY=audit-readable-marker$' /run/auto-parto/active/audit/secrets.properties
                """);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static void assertAuditBundleReadable(GenericContainer<?> controller) throws Exception {
        var result = controller.execInContainer("sh", "-ec", """
                test -r /run/auto-parto/active/audit/secrets.properties
                grep -q '^AUDIT_TEST_PROPERTY=audit-readable-marker$' /run/auto-parto/active/audit/secrets.properties
                """);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static void assertApplicationLockReleased(GenericContainer<?> controller) throws Exception {
        var result = controller.execInContainer("sh", "-ec",
                "test ! -e /run/lock/auto-parto-secret-delivery-application");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static void assertRoleBundle(GenericContainer<?> controller, String path, String expectedLine)
            throws Exception {
        var result = controller.execInContainer("sh", "-ec", "test -r '" + path + "' && grep -Fqx '"
                + expectedLine + "' '" + path + "'");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static String activeTarget(GenericContainer<?> controller) throws Exception {
        var result = controller.execInContainer("readlink", "/run/auto-parto/active");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return result.getStdout().trim();
    }

    private static String bundleSnapshot(GenericContainer<?> controller) throws Exception {
        var result = controller.execInContainer("sh", "-ec", """
                stat -c '%d:%i:%Y:%a:%u:%g' /run/auto-parto/active/secrets/secrets.properties
                stat -c '%d:%i:%Y:%a:%u:%g' /run/auto-parto/active/audit/secrets.properties
                """);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return result.getStdout().trim();
    }

    private static String createVolume(String prefix) {
        String volume = prefix + UUID.randomUUID().toString().replace('-', '_');
        DockerClientFactory.instance().client().createVolumeCmd().withName(volume).exec();
        return volume;
    }

    private static void removeVolume(String volume) {
        DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
    }
}
