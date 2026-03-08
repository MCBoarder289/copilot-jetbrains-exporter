package io.github.copilotjetbrains;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.copilotjetbrains.model.AgentSession;
import io.github.copilotjetbrains.model.AgentTurn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlExporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private AgentSession twoTurnSession() {
        return new AgentSession(
                "test-session-id",
                "Test Session",
                "testuser",
                1_700_000_000_000L,
                1_700_000_010_000L,
                List.of(
                        new AgentTurn("test-session-id", 1_700_000_001_000L, false,
                                "What is 2+2?", "2+2 equals 4.", "gpt-4o", "agent"),
                        new AgentTurn("test-session-id", 1_700_000_002_000L, false,
                                "And 3+3?", "3+3 equals 6.", "gpt-4o", "agent")
                )
        );
    }

    // --- buildLines: event types ---

    @Test
    void buildLines_firstLineIsSessionStart() throws Exception {
        List<String> lines = JsonlExporter.buildLines(twoTurnSession());
        JsonNode first = MAPPER.readTree(lines.get(0));
        assertThat(first.path("type").asText()).isEqualTo("session.start");
    }

    @Test
    void buildLines_containsUserMessage() {
        List<String> lines = JsonlExporter.buildLines(twoTurnSession());
        assertThat(lines).anyMatch(l -> l.contains("\"user.message\""));
    }

    @Test
    void buildLines_containsAssistantMessage() {
        List<String> lines = JsonlExporter.buildLines(twoTurnSession());
        assertThat(lines).anyMatch(l -> l.contains("\"assistant.message\""));
    }

    // --- buildLines: line count ---

    @Test
    void buildLines_lineCountMatchesTurns() {
        // 1 session.start + 2 turns × (user.message + assistant.message) = 5
        assertThat(JsonlExporter.buildLines(twoTurnSession())).hasSize(5);
    }

    @Test
    void buildLines_skipsBlankRequestText() {
        AgentSession session = new AgentSession(
                "id", "title", "user", 0L, 0L,
                List.of(new AgentTurn("id", 0L, false, "", "response only", "gpt-4o", "agent"))
        );
        // 1 session.start + 0 user.message + 1 assistant.message = 2
        assertThat(JsonlExporter.buildLines(session)).hasSize(2);
    }

    @Test
    void buildLines_skipsBlankResponseText() {
        AgentSession session = new AgentSession(
                "id", "title", "user", 0L, 0L,
                List.of(new AgentTurn("id", 0L, false, "request only", "", "gpt-4o", "agent"))
        );
        // 1 session.start + 1 user.message + 0 assistant.message = 2
        assertThat(JsonlExporter.buildLines(session)).hasSize(2);
    }

    // --- buildLines: content correctness ---

    @Test
    void buildLines_userMessageHasCorrectContent() throws Exception {
        List<String> lines = JsonlExporter.buildLines(twoTurnSession());
        JsonNode userMsg = lines.stream()
                .map(l -> { try { return MAPPER.readTree(l); } catch (Exception e) { return MAPPER.createObjectNode(); } })
                .filter(n -> "user.message".equals(n.path("type").asText()))
                .findFirst().orElseThrow();
        assertThat(userMsg.path("data").path("content").asText()).isEqualTo("What is 2+2?");
    }

    @Test
    void buildLines_sessionStartContainsSessionId() throws Exception {
        List<String> lines = JsonlExporter.buildLines(twoTurnSession());
        JsonNode start = MAPPER.readTree(lines.get(0));
        assertThat(start.path("data").path("sessionId").asText()).isEqualTo("test-session-id");
    }

    @Test
    void buildLines_allLinesAreValidJson() {
        JsonlExporter.buildLines(twoTurnSession()).forEach(line ->
                assertThat(line).satisfies(l -> {
                    try { MAPPER.readTree(l); } catch (Exception e) {
                        throw new AssertionError("Invalid JSON: " + l, e);
                    }
                })
        );
    }

    // --- file writing ---

    @Test
    void export_writesFileIntoSessionStateSubdir() throws Exception {
        JsonlExporter.export(List.of(twoTurnSession()), tempDir, false, false);
        Path expected = tempDir.resolve("session-state").resolve("test-session-id.jsonl");
        assertThat(expected).exists();
    }

    @Test
    void export_writtenFileContainsValidJsonLines() throws Exception {
        JsonlExporter.export(List.of(twoTurnSession()), tempDir, false, false);
        Path file = tempDir.resolve("session-state").resolve("test-session-id.jsonl");
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).isNotEmpty();
        lines.forEach(line ->
                assertThat(line).satisfies(l -> {
                    try { MAPPER.readTree(l); } catch (Exception e) {
                        throw new AssertionError("Invalid JSON line: " + l, e);
                    }
                })
        );
    }

    @Test
    void export_dryRunDoesNotCreateAnyFiles() throws Exception {
        JsonlExporter.export(List.of(twoTurnSession()), tempDir, true, false);
        assertThat(Files.exists(tempDir.resolve("session-state"))).isFalse();
    }

    @Test
    void export_returnsCountOfExportedSessions() throws Exception {
        int count = JsonlExporter.export(
                List.of(twoTurnSession(), twoTurnSession()), tempDir, false, false);
        assertThat(count).isEqualTo(2);
    }
}
