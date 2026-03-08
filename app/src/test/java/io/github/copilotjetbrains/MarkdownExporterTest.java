package io.github.copilotjetbrains;

import io.github.copilotjetbrains.model.AgentSession;
import io.github.copilotjetbrains.model.AgentTurn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownExporterTest {

    @TempDir
    Path tempDir;

    private AgentSession oneTurnSession() {
        return new AgentSession(
                "test-session-id",
                "Python Debugging",
                "testuser",
                1_700_000_000_000L,
                1_700_000_010_000L,
                List.of(
                        new AgentTurn("test-session-id", 1_700_000_001_000L, false,
                                "How do I fix this bug?", "Here is the fix: check line 42.",
                                "gpt-4o", "agent")
                )
        );
    }

    // --- buildMarkdown: structure ---

    @Test
    void buildMarkdown_containsH1Title() {
        String md = MarkdownExporter.buildMarkdown(oneTurnSession());
        assertThat(md).contains("# Python Debugging");
    }

    @Test
    void buildMarkdown_containsDateLine() {
        String md = MarkdownExporter.buildMarkdown(oneTurnSession());
        assertThat(md).contains("**Date:**");
        assertThat(md).containsPattern("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void buildMarkdown_containsUserField() {
        String md = MarkdownExporter.buildMarkdown(oneTurnSession());
        assertThat(md).contains("**User:** testuser");
    }

    @Test
    void buildMarkdown_containsSessionId() {
        String md = MarkdownExporter.buildMarkdown(oneTurnSession());
        assertThat(md).contains("test-session-id");
    }

    @Test
    void buildMarkdown_containsUserPrompt() {
        String md = MarkdownExporter.buildMarkdown(oneTurnSession());
        assertThat(md).contains("**User:**");
        assertThat(md).contains("How do I fix this bug?");
    }

    @Test
    void buildMarkdown_containsAssistantResponse() {
        String md = MarkdownExporter.buildMarkdown(oneTurnSession());
        assertThat(md).contains("**Assistant:**");
        assertThat(md).contains("Here is the fix:");
    }

    @Test
    void buildMarkdown_containsModelName() {
        String md = MarkdownExporter.buildMarkdown(oneTurnSession());
        assertThat(md).contains("gpt-4o");
    }

    @Test
    void buildMarkdown_multipleTurnsHaveNumberedHeaders() {
        AgentSession session = new AgentSession(
                "id", "Multi-turn", "user", 0L, 0L,
                List.of(
                        new AgentTurn("id", 1L, false, "q1", "a1", "gpt-4o", "agent"),
                        new AgentTurn("id", 2L, false, "q2", "a2", "gpt-4o", "agent")
                )
        );
        String md = MarkdownExporter.buildMarkdown(session);
        assertThat(md).contains("## Turn 1");
        assertThat(md).contains("## Turn 2");
    }

    // --- buildFilename ---

    @Test
    void buildFilename_includesDatePrefix() {
        String filename = MarkdownExporter.buildFilename(oneTurnSession());
        assertThat(filename).matches("2023-11-\\d{2}_.*\\.md");
    }

    @Test
    void buildFilename_includesSanitizedTitle() {
        String filename = MarkdownExporter.buildFilename(oneTurnSession());
        assertThat(filename).contains("Python_Debugging");
    }

    @Test
    void buildFilename_endsWithMdExtension() {
        assertThat(MarkdownExporter.buildFilename(oneTurnSession())).endsWith(".md");
    }

    @Test
    void buildFilename_removesSpecialCharacters() {
        AgentSession session = new AgentSession(
                "id", "Title: with? special* chars!", "user",
                1_700_000_000_000L, 1_700_000_000_000L, List.of()
        );
        String filename = MarkdownExporter.buildFilename(session);
        assertThat(filename).doesNotContain(":", "?", "*", "!");
    }

    @Test
    void buildFilename_collapsesMultipleSpacesToUnderscores() {
        AgentSession session = new AgentSession(
                "id", "lots   of   spaces", "user",
                1_700_000_000_000L, 1_700_000_000_000L, List.of()
        );
        String filename = MarkdownExporter.buildFilename(session);
        assertThat(filename).doesNotContain("   ");
        assertThat(filename).contains("lots_of_spaces");
    }

    // --- file writing ---

    @Test
    void export_writesMarkdownFileToOutputDir() throws Exception {
        MarkdownExporter.export(List.of(oneTurnSession()), tempDir, false, false);
        long mdCount = Files.list(tempDir).filter(p -> p.toString().endsWith(".md")).count();
        assertThat(mdCount).isEqualTo(1);
    }

    @Test
    void export_writtenFileContainsTitle() throws Exception {
        MarkdownExporter.export(List.of(oneTurnSession()), tempDir, false, false);
        String content = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".md"))
                .findFirst().map(p -> { try { return Files.readString(p); } catch (Exception e) { return ""; } })
                .orElse("");
        assertThat(content).contains("# Python Debugging");
    }

    @Test
    void export_dryRunDoesNotWriteAnyFiles() throws Exception {
        MarkdownExporter.export(List.of(oneTurnSession()), tempDir, true, false);
        assertThat(Files.list(tempDir).count()).isEqualTo(0);
    }

    @Test
    void export_skipsSessionsWithNoTurns() throws Exception {
        AgentSession empty = new AgentSession("id", "Empty", "user", 0L, 0L, List.of());
        int count = MarkdownExporter.export(List.of(empty), tempDir, false, false);
        assertThat(count).isEqualTo(0);
    }
}
