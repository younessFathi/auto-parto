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

        assertThat(secretDecision).contains("**Status:** Ready")
                .contains("ADR-0001 records the product owner's selection")
                .contains("operator-controlled Ansible Vault deploy-time injection")
                .contains("ordinary CI secrets")
                .contains("separately approved secure unlock/retrieval design")
                .contains("Do not mark it Done");
        assertThat(infrastructure).contains("**Status:** Proposed")
                .contains("ADR-0001 resolves the secret model in favor of Ansible Vault")
                .contains("Contabo was selected as the intended provider on 2026-10-30")
                .contains("requires a paid add-on per participating server")
                .contains("has no built-in firewall")
                .contains("https://github.com/contabo/terraform-provider-contabo")
                .contains("monthly cost estimate")
                .contains("maintained OpenTofu provider")
                .contains("state-changing migration/deployment runner")
                .contains("Never expose PostgreSQL publicly")
                .contains("Keep GitLab CI active until");
        String adr = Files.readString(Path.of("docs/architecture-decisions/ADR-0001-production-secret-delivery.md"));
        assertThat(adr).contains("**Status:** Accepted")
                .contains("Use **Ansible Vault with operator-controlled deploy-time injection**")
                .contains("Never store the password in Git, on any VPS, in a command line, logs, traces, or ordinary CI secrets")
                .contains("Do not use OpenBao or another runtime secret service to deliver the same production credentials");
    }

    @Test
    void secretAndInfrastructurePlanningDoesNotCloseDependentReadinessWork() throws IOException {
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0022-openbao-secrets.md")))
                .contains("**Status:** Cancelled")
                .contains("ADR-0001");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0044-openbao-vps-go-live.md")))
                .contains("**Status:** Cancelled")
                .contains("ADR-0001");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0045-ansible-vault-vps-secrets.md")))
                .contains("**Status:** Ready");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0026-data-infrastructure.md")))
                .contains("**Status:** Proposed");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0033-github-actions-migration.md")))
                .contains("## Status\n\nIn Progress");
        assertThat(Files.readString(BACKLOG.resolve("features/F07-deployment-and-secrets.md")))
                .contains("**Status:** In Progress");
        assertThat(Files.readString(BACKLOG.resolve("features/F09-go-live-readiness.md")))
                .contains("**Status:** Deferred until a go-live decision is approved.");
        assertThat(Files.readString(BACKLOG.resolve("tickets/AP-0024-alerts-recovery.md")))
                .contains("AP-0045 (selected Ansible Vault delivery")
                .contains("AP-0022's OpenBao production scope is cancelled");
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
