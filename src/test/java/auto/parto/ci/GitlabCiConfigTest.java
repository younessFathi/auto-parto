package auto.parto.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabCiConfigTest {

    @Test
    void usesPinnedJava21MavenImageAndNoTargetCache() throws IOException {
        String content = Files.readString(Path.of(".gitlab-ci.yml"));

        assertThat(content).contains("image: maven:3.9.10-eclipse-temurin-21");
        assertThat(content).contains(".m2/repository");
        assertThat(content).doesNotContain("cache:\n  key: \"$CI_COMMIT_REF_SLUG\"\n  paths:\n    - .m2/repository # Cache Maven dependencies\n    - target/");
    }

    @Test
    void doesNotContainHardcodedSecrets() throws IOException {
        String content = Files.readString(Path.of(".gitlab-ci.yml"));

        assertThat(content).doesNotContain("bmgTK4gXGmWEbq3jc3zluPcMtomqqpMSO1VY7R0QqYQ=");
    }

    @Test
    void jwtSecretIsNotDefinedInVariables() throws IOException {
        String content = Files.readString(Path.of(".gitlab-ci.yml"));

        assertThat(content).doesNotContain("JWT_SECRET:");
    }

    @Test
    void testAndVerifyJobsRunWithCleanAndExposeReports() throws IOException {
        String content = Files.readString(Path.of(".gitlab-ci.yml"));

        assertThat(content).contains("mvn -B -e clean test");
        assertThat(content).contains("target/surefire-reports/");
        assertThat(content).contains("mvn -B -e clean verify");
        assertThat(content).contains("target/failsafe-reports/");
    }

    @Test
    void doesNotArchiveBuildOrPackageOutputsAndRetainsOnlyShortLivedTestReports() throws IOException {
        String content = Files.readString(Path.of(".gitlab-ci.yml"));

        assertThat(content).doesNotContain("- target/\n")
                .doesNotContain("target/*.jar")
                .contains("junit: target/surefire-reports/*.xml")
                .contains("junit: target/failsafe-reports/*.xml")
                .contains("expire_in: 1 day");
    }
}
