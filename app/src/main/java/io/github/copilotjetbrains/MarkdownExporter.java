package io.github.copilotjetbrains;

import io.github.copilotjetbrains.model.AgentSession;
import io.github.copilotjetbrains.model.AgentTurn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Exports sessions to human-readable Markdown files.
 *
 * <p>Each session is written as one {@code .md} file in the output directory.
 * Filenames are prefixed with the session date for easy sorting.
 */
public final class MarkdownExporter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    private MarkdownExporter() {}

    /**
     * Exports all sessions to Markdown files in {@code outputDir}.
     *
     * @param sessions  sessions to export
     * @param outputDir directory to write {@code .md} files into (created if absent)
     * @param dryRun    if true, prints what would be written without touching the filesystem
     * @param verbose   if true, prints each file path as it is written
     * @return number of files written (or that would be written in dry-run mode)
     */
    public static int export(List<AgentSession> sessions, Path outputDir,
                             boolean dryRun, boolean verbose) throws IOException {
        if (!dryRun) {
            Files.createDirectories(outputDir);
        }

        int count = 0;
        for (AgentSession session : sessions) {
            if (session.turns().isEmpty()) continue;

            Path outFile = outputDir.resolve(buildFilename(session));
            String content = buildMarkdown(session);

            if (dryRun || verbose) {
                System.out.printf("%s  %s  (%d turns)%n",
                        dryRun ? "[dry-run]" : "Writing",
                        outFile, session.turns().size());
            }
            if (!dryRun) {
                Files.writeString(outFile, content, StandardCharsets.UTF_8);
            }
            count++;
        }
        return count;
    }

    /**
     * Builds a safe filename for the session.
     * Format: {@code YYYY-MM-DD_Title_Slug.md}
     * Exposed package-private for unit testing.
     */
    static String buildFilename(AgentSession session) {
        String date = DATE_FMT.format(Instant.ofEpochMilli(session.createdAtMs()));
        String safe = session.title()
                .replaceAll("[^\\p{L}\\p{N}\\s-]", "")  // keep letters, digits, spaces, hyphens
                .replaceAll("\\s+", "_")
                .replaceAll("^_+|_+$", "");
        if (safe.isBlank()) safe = session.id();
        if (safe.length() > 60) safe = safe.substring(0, 60);
        return date + "_" + safe + ".md";
    }

    /**
     * Renders a session as a Markdown document.
     * Exposed package-private for unit testing.
     */
    static String buildMarkdown(AgentSession session) {
        StringBuilder sb = new StringBuilder();

        // Document header
        sb.append("# ").append(session.title()).append("\n\n");
        sb.append("**Date:** ")
                .append(DATE_FMT.format(Instant.ofEpochMilli(session.createdAtMs())))
                .append("  \n");
        if (session.user() != null && !session.user().isBlank()) {
            sb.append("**User:** ").append(session.user()).append("  \n");
        }
        sb.append("**Session ID:** `").append(session.id()).append("`\n\n");
        sb.append("---\n\n");

        // Turns
        int turnNum = 1;
        for (AgentTurn turn : session.turns()) {
            sb.append("## Turn ").append(turnNum++).append("\n\n");

            String req = turn.requestText();
            if (req != null && !req.isBlank()) {
                sb.append("**User:**\n\n");
                sb.append(req.strip()).append("\n\n");
            }

            String res = turn.responseText();
            if (res != null && !res.isBlank()) {
                sb.append("**Assistant:**");
                if (turn.modelName() != null && !turn.modelName().isBlank()) {
                    sb.append(" *(").append(turn.modelName()).append(")*");
                }
                sb.append("\n\n");
                sb.append(res.strip()).append("\n\n");
            }

            sb.append("---\n\n");
        }

        return sb.toString();
    }
}
