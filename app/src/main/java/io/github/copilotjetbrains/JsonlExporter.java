package io.github.copilotjetbrains;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.copilotjetbrains.model.AgentSession;
import io.github.copilotjetbrains.model.AgentTurn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports sessions to the agentsview Copilot JSONL format.
 *
 * <p>Each session is written as a {@code .jsonl} file under
 * {@code <outputDir>/session-state/<sessionId>.jsonl}. The format is identical to the
 * Copilot CLI JSONL format that agentsview already understands, so no changes to
 * agentsview are required — just point its {@code COPILOT_DIR} at the output directory.
 *
 * <p>Event types emitted per session:
 * <ul>
 *   <li>{@code session.start} — once, at the top of the file</li>
 *   <li>{@code user.message} — one per turn (if request text is non-blank)</li>
 *   <li>{@code assistant.message} — one per turn (if response text is non-blank)</li>
 * </ul>
 */
public final class JsonlExporter {

    private static final ObjectMapper MAPPER     = new ObjectMapper();
    private static final String       STATE_DIR  = "session-state";

    private JsonlExporter() {}

    /**
     * Exports all sessions to JSONL files under {@code outputDir/session-state/}.
     *
     * @param sessions  sessions to export
     * @param outputDir root output directory (will be created if it does not exist)
     * @param dryRun    if true, prints what would be written without touching the filesystem
     * @param verbose   if true, prints each file path as it is written
     * @return number of session files written (or that would be written in dry-run mode)
     */
    public static int export(List<AgentSession> sessions, Path outputDir,
                             boolean dryRun, boolean verbose) throws IOException {
        Path sessionStateDir = outputDir.resolve(STATE_DIR);
        if (!dryRun) {
            Files.createDirectories(sessionStateDir);
        }

        int count = 0;
        for (AgentSession session : sessions) {
            List<String> lines = buildLines(session);
            if (lines.isEmpty()) continue;

            Path outFile = sessionStateDir.resolve(session.id() + ".jsonl");
            if (dryRun || verbose) {
                System.out.printf("%s  %s  (%d turns, %d events)%n",
                        dryRun ? "[dry-run]" : "Writing",
                        outFile, session.turns().size(), lines.size());
            }
            if (!dryRun) {
                Files.write(outFile, lines, StandardCharsets.UTF_8);
            }
            count++;
        }
        return count;
    }

    /**
     * Builds the ordered list of JSONL event lines for one session.
     * Exposed package-private for unit testing.
     */
    static List<String> buildLines(AgentSession session) {
        List<String> lines = new ArrayList<>();

        lines.add(event("session.start", session.createdAtMs(), sessionStartData(session)));

        for (AgentTurn turn : session.turns()) {
            String req = turn.requestText();
            String res = turn.responseText();

            if (req != null && !req.isBlank()) {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("content", req);
                lines.add(event("user.message", turn.createdAtMs(), data));
            }
            if (res != null && !res.isBlank()) {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("content", res);
                lines.add(event("assistant.message", turn.createdAtMs(), data));
            }
        }

        return lines;
    }

    private static ObjectNode sessionStartData(AgentSession session) {
        ObjectNode data    = MAPPER.createObjectNode();
        ObjectNode context = data.putObject("context");
        data.put("sessionId", session.id());
        // JetBrains sessions don't expose the project working directory.
        // agentsview will fall back to displaying the session title.
        context.put("cwd",    "");
        context.put("branch", "");
        // Title is passed as a hint; agentsview's Copilot parser ignores unknown fields.
        data.put("title", session.title());
        return data;
    }

    private static String event(String type, long timestampMs, ObjectNode data) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type",      type);
            root.put("timestamp", Instant.ofEpochMilli(timestampMs).toString());
            root.set("data",      data);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event of type: " + type, e);
        }
    }
}
