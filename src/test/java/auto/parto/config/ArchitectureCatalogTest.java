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
