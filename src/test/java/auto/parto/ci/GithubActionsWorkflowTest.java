package auto.parto.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GithubActionsWorkflowTest {

    private static final Path WORKFLOW = Path.of(".github/workflows/ci.yml");

    @Test
    void workflowRunsBuildUnitIntegrationAndPackageStages() throws IOException {
        String content = Files.readString(WORKFLOW);

        assertThat(content).contains("actions/checkout@v4")
                .contains("actions/setup-java@v4")
                .contains("- 'fix/**'")
                .contains("./mvnw -B -e clean compile")
                .contains("./mvnw -B -e clean test")
                .contains("./mvnw -B -e clean verify")
                .contains("./mvnw -B -e clean package -DskipTests");
    }

    @Test
    void workflowRetainsShortLivedTestReportsWithoutPackagingArtifacts() throws IOException {
        String content = Files.readString(WORKFLOW);

        assertThat(content).contains("actions/upload-artifact@v4")
                .contains("path: target/surefire-reports/")
                .contains("path: target/failsafe-reports/")
                .contains("retention-days: 1")
                .doesNotContain("path: target/*.jar");
    }

    @Test
    void workflowExposesTheOptionalProtectedMainOnlyCutoverGate() throws IOException {
        String content = Files.readString(WORKFLOW);

        assertThat(content).contains("workflow_dispatch:")
                .contains("run_cutover_gate:")
                .contains("github.ref == 'refs/heads/main'")
                .contains("name: postgresql-cutover")
                .contains("flyway/flyway:10.20.1")
                .contains("APP_POSTGRES_MIGRATION_JDBC_URL")
                .contains("APP_POSTGRES_RUNTIME_PASSWORD")
                .contains("APP_POSTGRES_BACKUP_REFERENCE")
                .contains("--env FLYWAY_PASSWORD")
                .contains("preflight-cutover.sh");
        assertThat(content).doesNotContain("-password=\"$APP_POSTGRES_MIGRATION_PASSWORD\"")
                .doesNotContain("-e PGPASSWORD=\"$APP_POSTGRES_RUNTIME_PASSWORD\"");

        int backupGate = content.indexOf("bash deploy/postgres/operations/preflight-cutover.sh");
        int flyway = content.indexOf("flyway/flyway:10.20.1");
        int fixtures = content.indexOf("identity-fixtures.sql");
        int reconciliation = content.indexOf("reconcile-development-fixtures.sh");
        int finalPreflight = content.lastIndexOf("bash deploy/postgres/operations/preflight-cutover.sh");
        assertThat(backupGate).isLessThan(flyway);
        assertThat(flyway).isLessThan(fixtures);
        assertThat(fixtures).isLessThan(reconciliation);
        assertThat(reconciliation).isLessThan(finalPreflight);
    }
}
