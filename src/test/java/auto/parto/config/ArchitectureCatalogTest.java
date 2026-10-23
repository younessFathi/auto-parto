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
    void reconciledStatusesAreRecorded() throws IOException {
        assertStatus("features/F01-platform-and-data.md", "In Progress");
        assertStatus("features/F02-identity-and-security.md", "Done");
        assertStatus("features/F03-marketplace.md", "Done");
        assertStatus("features/F04-money-and-wallets.md", "Done");
        assertStatus("features/F05-notifications-and-redis.md", "Done");
        assertStatus("features/F06-file-storage.md", "Done");
        assertStatus("features/F07-deployment-and-secrets.md", "In Progress");
        assertStatus("features/F08-observability-and-recovery.md", "Proposed");
        assertStatus("features/F09-go-live-readiness.md", "Deferred until a go-live decision is approved.");
        assertStatus("features/F12-runtime-cleanup.md", "Done");
        assertStatus("tickets/AP-0025-api-error-contract.md", "Done");
        assertStatus("tickets/AP-0030-application-boundaries.md", "Proposed");
        assertStatus("tickets/AP-0033-github-actions-migration.md", "Done");
        assertStatus("tickets/AP-0037-remove-mongodb.md", "Done");
        assertStatus("tickets/AP-0041-architecture-catalog-repair.md", "Done");
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

    private void assertStatus(String relativePath, String status) throws IOException {
        assertThat(Files.readString(BACKLOG.resolve(relativePath)))
                .as("status in %s", relativePath)
                .satisfies(content -> assertThat(content).containsAnyOf("**Status:** " + status, "## Status\n\n" + status + ".", "## Status\n\n" + status));
    }
}
