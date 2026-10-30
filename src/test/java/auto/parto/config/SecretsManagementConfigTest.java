package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretsManagementConfigTest {

    @Test
    void gitignoreContainsEnv() throws IOException {
        String gitignore = Files.readString(Path.of(".gitignore"));
        assertThat(gitignore).contains(".env");
    }

    @Test
    void envExampleExists() {
        assertThat(Path.of(".env.example")).exists().isRegularFile();
    }

    @Test
    void envExampleContainsAllRequiredEnvironmentVariables() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        assertThat(content)
                .contains("SPRING_PROFILES_ACTIVE")
                .contains("APP_POSTGRES_R2DBC_URL")
                .contains("REDIS_HOST")
                .contains("REDIS_PORT")
                .contains("REDIS_PASSWORD")
                .contains("JWT_SECRET")
                .contains("FIREBASE_ENABLED")
                .contains("FIREBASE_CREDENTIALS_JSON")
                .contains("RESEND_API_KEY");
    }

    @Test
    void envExampleUsesPlaceholderValuesNotRealSecrets() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        List.of(
                "your-resend-api-key",
                "your-256-bit-base64-encoded-secret"
        ).forEach(placeholder ->
                assertThat(content).contains(placeholder)
        );

        assertThat(content)
                .doesNotContain("GOOGLE_CLIENT_SECRET_PREFIX-")
                .doesNotContain("AC[REDACTED]")
                .doesNotContain("re_[REDACTED]")
                .doesNotContain("GOOGLE_PROJECT_NUMBER");
    }

    @Test
    void envExampleHasNoHardcodedRealValues() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        assertThat(content)
                .doesNotContain("root:root")
                .doesNotContain("password: root");
    }

    @Test
    void productionProfileUsesRequiredExternalPlaceholders() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(content)
                .contains("url: ${APP_POSTGRES_R2DBC_URL}")
                .contains("file:/run/auto-parto/secrets/secrets.properties")
                .contains("provider: ${APP_FILE_STORAGE_PROVIDER}")
                .contains("MongoReactiveAutoConfiguration")
                .contains("mongo-disabled: true")
                .doesNotContain("localhost")
                .doesNotContain("root")
                .doesNotContain("openbao")
                .doesNotContain("APP_SECRET_PROVIDER")
                .doesNotContain("JWT_SECRET");
    }

    @Test
    void productionComposeUsesOnlyReadOnlyRoleScopedFileMounts() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.prod.yml"));
        assertThat(compose)
                .contains("/run/auto-parto/active:/run/auto-parto:ro")
                .contains("user: \"10001:10001\"")
                .doesNotContain("OPENBAO_ADDR")
                .doesNotContain("OPENBAO_ROLE")
                .doesNotContain("APP_SECRET_PROVIDER");
    }

    @Test
    void ansibleVaultPlaybookValidatesEncryptedInputsAndSuppressesSecretOutput() throws IOException {
        String playbook = Files.readString(Path.of("deploy/ansible/deploy-secret-bundles.yml"));
        Object parsedPlaybook = new org.yaml.snakeyaml.Yaml().load(playbook);
        assertThat(parsedPlaybook).isInstanceOf(List.class);
        String activation = Files.readString(Path.of("deploy/ansible/activate-secret-generation.sh"));
        assertThat(playbook)
                .contains("hosts: secret_bundle_targets")
                .contains("head -c 15")
                .contains("$ANSIBLE_VAULT;")
                .contains("decrypt: true")
                .contains("mode: \"0440\"")
                .contains("secret_target_role")
                .contains("application_database")
                .contains("migration_runner")
                .contains("audit-runtime")
                .contains("item.gid | int == 10001")
                .contains("generation_path == secret_bundle_paths[item.role]")
                .contains("generations/{{ secret_generation_id }}")
                .contains("Secret bundle staging failed")
                .contains("activate-secret-generation.sh")
                .contains("no_log: true")
                .contains("diff: false")
                .doesNotContain("vault_password_file")
                .doesNotContain("debug:");
        assertThat(activation)
                .contains("test -f \"$generation_root/$relative_path\"")
                .contains("mv -Tf")
                .contains("/run/auto-parto:secrets/secrets.properties")
                .doesNotContain("eval ");
    }

    @Test
    void openBaoDeliveryDoesNotPlaceAgentTokenInApplicationCompose() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.prod.yml"));
        String agent = Files.readString(Path.of("deploy/openbao/agent.hcl.example"));
        assertThat(compose).contains("/run/auto-parto/active:/run/auto-parto:ro")
                .doesNotContain("OPENBAO_TOKEN")
                .doesNotContain("BAO_TOKEN");
        assertThat(agent).contains("secret_id_response_wrapping_path")
                .contains("/run/openbao-agent/token")
                .doesNotContain("root_token");
    }

    @Test
    void canonicalAuthenticationDocumentationMatchesProductionJwtContract() throws IOException {
        String content = Files.readString(Path.of("docs/canonical-authentication.md"));

        assertThat(content)
                .contains("non-production profiles retain HS256 compatibility")
                .contains("The `prod` profile requires RS256 signing material")
                .contains("`/.well-known/jwks.json`")
                .doesNotContain("The JWT signing secret is required through `JWT_SECRET`");
    }

    @Test
    void openBaoInstallerEnablesRequiredEnginesAndShortLivedScopedRoles() throws IOException {
        String installer = Files.readString(Path.of("deploy/openbao/install-policies.sh"));

        assertThat(installer)
                .contains("bao auth enable approle")
                .contains("bao secrets enable -path=kv kv-v2")
                .contains("bao secrets enable database")
                .contains("token_ttl=15m")
                .contains("token_max_ttl=1h")
                .contains("secret_id_ttl=10m")
                .contains("secret_id_num_uses=1")
                .contains("bind_secret_id=true")
                .doesNotContain("root_token");

        String applicationPolicy = Files.readString(Path.of("deploy/openbao/policies/app.hcl"));
        String auditPolicy = Files.readString(Path.of("deploy/openbao/policies/audit.hcl"));
        String migrationPolicy = Files.readString(Path.of("deploy/openbao/policies/migration.hcl"));
        assertThat(applicationPolicy).contains("kv/data/production/application/")
                .contains("database/creds/production-application")
                .doesNotContain("production-audit")
                .doesNotContain("production-migration");
        assertThat(auditPolicy).contains("kv/data/production/audit/")
                .contains("database/creds/production-audit")
                .doesNotContain("production-application")
                .doesNotContain("production-migration");
        assertThat(migrationPolicy).contains("database/creds/production-migration")
                .doesNotContain("production-application")
                .doesNotContain("production-audit");
    }
}
