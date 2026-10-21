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
    void workflowExposesTheOptionalProtectedCutoverGate() throws IOException {
        String content = Files.readString(WORKFLOW);

        assertThat(content).contains("workflow_dispatch:")
                .contains("run_cutover_gate:")
                .contains("flyway/flyway:10.20.1")
                .contains("APP_POSTGRES_MIGRATION_JDBC_URL")
                .contains("APP_POSTGRES_RUNTIME_PASSWORD")
                .contains("preflight-cutover.sh");
    }
}
