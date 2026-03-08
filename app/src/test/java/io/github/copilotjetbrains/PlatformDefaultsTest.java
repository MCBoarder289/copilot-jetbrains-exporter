package io.github.copilotjetbrains;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDefaultsTest {

    @Test
    void defaultSourceDirs_returnsAtLeastOneDir() {
        assertThat(PlatformDefaults.defaultSourceDirs()).isNotEmpty();
    }

    @Test
    void defaultSourceDirs_allPathsAreAbsolute() {
        for (Path dir : PlatformDefaults.defaultSourceDirs()) {
            assertThat(dir.isAbsolute())
                    .as("Expected absolute path but got: %s", dir)
                    .isTrue();
        }
    }

    @Test
    void defaultSourceDirs_containsGithubCopilotSegment() {
        List<Path> dirs = PlatformDefaults.defaultSourceDirs();
        boolean hasGithubCopilot = dirs.stream()
                .anyMatch(p -> p.toString().contains("github-copilot"));
        assertThat(hasGithubCopilot)
                .as("At least one source dir should reference 'github-copilot', got: %s", dirs)
                .isTrue();
    }

    @Test
    void defaultOutputDir_jsonl_isAbsolute() {
        assertThat(PlatformDefaults.defaultOutputDir(Main.Format.JSONL).isAbsolute()).isTrue();
    }

    @Test
    void defaultOutputDir_jsonl_containsCopilot() {
        Path output = PlatformDefaults.defaultOutputDir(Main.Format.JSONL);
        assertThat(output.toString())
                .as("JSONL output should live in a copilot-related path: %s", output)
                .contains("copilot");
    }

    @Test
    void defaultOutputDir_markdown_isAbsolute() {
        assertThat(PlatformDefaults.defaultOutputDir(Main.Format.MARKDOWN).isAbsolute()).isTrue();
    }

    @Test
    void defaultOutputDir_jsonlAndMarkdownAreDifferent() {
        Path jsonlOut    = PlatformDefaults.defaultOutputDir(Main.Format.JSONL);
        Path markdownOut = PlatformDefaults.defaultOutputDir(Main.Format.MARKDOWN);
        assertThat(jsonlOut).isNotEqualTo(markdownOut);
    }
}
