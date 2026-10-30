package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureCatalogTest {
    private static final Path BACKLOG = Path.of(".architecture-backlog");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)#]+\\.md)(?:#[^)]*)?\\)");

    @Test
    void activeCatalogLinksResolve() throws IOException {
        try (var paths = Files.walk(BACKLOG)) {
            paths.filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.toString().contains("/reviews/"))
                    .forEach(this::assertLinksResolve);
        }
    }

    @Test
    void featureIndexContainsAllActiveFeatures() throws IOException {
        String index = Files.readString(BACKLOG.resolve("README.md"));
        List<String> features = List.of(
                "F01-platform-and-data.md", "F02-identity-and-security.md", "F03-marketplace.md",
                "F04-money-and-wallets.md", "F05-notifications-and-redis.md", "F06-file-storage.md",
                "F07-deployment-and-secrets.md", "F08-observability-and-recovery.md",
                "F09-go-live-readiness.md", "F12-runtime-cleanup.md");
        for (String feature : features) {
            assertThat(index).contains("features/" + feature);
            assertThat(Files.exists(BACKLOG.resolve("features").resolve(feature))).isTrue();
        }
    }

    @Test
    void activeCatalogEntriesDeclareAStatus() throws IOException {
        try (var paths = Files.walk(BACKLOG)) {
            paths.filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> path.startsWith(BACKLOG.resolve("features"))
                            || path.startsWith(BACKLOG.resolve("tickets")))
                    .forEach(this::assertStatusSyntax);
        }
    }

    @Test
    void productionSecretAndInfrastructurePlansKeepTheirDecisionBlockersExplicit() throws IOException {
        String secretDecision = Files.readString(BACKLOG.resolve("tickets/AP-0045-ansible-vault-vps-secrets.md"));
        String infrastructure = Files.readString(
                BACKLOG.resolve("tickets/AP-0046-provision-vps-topology-with-opentofu-ansible.md"));

        assertThat(secretDecision).contains("**Status:** Proposed")
                .contains("Architecture Decision Record (ADR)")
                .contains("selecting exactly one initial production secret-delivery model")
                .contains("operator-controlled Ansible Vault deploy-time injection")
                .contains("OpenBao/an approved protected runtime secret service")
                .contains("ordinary CI secrets")
                .contains("separately approved secure unlock/retrieval design")
                .contains("cancel AP-0045");
        assertThat(infrastructure).contains("**Status:** Proposed")
                .contains("remains Proposed until both the secret-delivery model and provider/private-network capability are selected")
                .contains("Contabo was selected as the intended provider on 2026-10-30")
                .contains("requires a paid add-on per participating server")
                .contains("has no built-in firewall")
                .contains("https://github.com/contabo/terraform-provider-contabo")
                .contains("dedicated protected secret-service infrastructure")
                .contains("monthly cost estimate")
                .contains("maintained OpenTofu provider")
                .contains("state-changing migration/deployment runner")
                .contains("Never expose PostgreSQL publicly")
                .contains("Keep GitLab CI active until");
    }

    @Test
    void secretAndInfrastructurePlanningDoesNotCloseDependentReadinessWork() throws IOException {
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0022-openbao-secrets.md")))
                .contains("**Status:** In Progress");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0044-openbao-vps-go-live.md")))
                .contains("**Status:** Proposed");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0026-data-infrastructure.md")))
                .contains("**Status:** Proposed");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0033-github-actions-migration.md")))
                .contains("## Status\n\nIn Progress");
        assertThat(Files.readString(BACKLOG.resolve("features/F07-deployment-and-secrets.md")))
                .contains("**Status:** In Progress");
        assertThat(Files.readString(BACKLOG.resolve("features/F09-go-live-readiness.md")))
                .contains("**Status:** Deferred until a go-live decision is approved.");
    }

    private void assertLinksResolve(Path markdown) {
        try {
            String content = Files.readString(markdown);
            var matcher = MARKDOWN_LINK.matcher(content);
            while (matcher.find()) {
                String target = matcher.group(1);
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    continue;
                }
                Path resolved = markdown.getParent().resolve(target).normalize();
                assertThat(Files.exists(resolved))
                        .as("Missing link %s in %s", target, markdown)
                        .isTrue();
            }
        } catch (IOException exception) {
            throw new AssertionError("Cannot read " + markdown, exception);
        }
    }

    private void assertStatusSyntax(Path markdown) {
        try {
            String content = Files.readString(markdown);
            assertThat(content)
                    .as("status declaration in %s", markdown)
                    .containsAnyOf("**Status:**", "## Status");
        } catch (IOException exception) {
            throw new AssertionError("Cannot read " + markdown, exception);
        }
    }
}
