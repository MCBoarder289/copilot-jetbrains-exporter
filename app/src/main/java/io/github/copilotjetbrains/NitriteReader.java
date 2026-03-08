package io.github.copilotjetbrains;

import io.github.copilotjetbrains.model.AgentSession;
import io.github.copilotjetbrains.model.AgentTurn;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.dizitart.no2.store.NitriteMap;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads JetBrains Copilot chat sessions from a Nitrite database file.
 *
 * <p>JetBrains stores sessions in a file named {@value #DB_FILENAME} inside the GitHub Copilot
 * config directory. This reader opens that file read-only and extracts session metadata and
 * conversation turns from the two relevant Nitrite collections.
 *
 * <p><strong>Compatibility note:</strong> The Nitrite library bundled in this tool must be
 * version-compatible with the one used by the JetBrains Copilot plugin (currently 4.2.x).
 * If JetBrains updates their plugin to an incompatible Nitrite version, opening the database
 * will throw an {@link IOException} with a descriptive message.
 */
public class NitriteReader implements Closeable {

    public static final String DB_FILENAME = "copilot-agent-sessions-nitrite.db";

    static final String SESSIONS_COLLECTION =
            "com.github.copilot.agent.session.persistence.nitrite.entity.NtAgentSession";
    static final String TURNS_COLLECTION =
            "com.github.copilot.agent.session.persistence.nitrite.entity.NtAgentTurn";

    private final Nitrite db;

    /**
     * Opens the Nitrite database at {@code dbPath} in read-only mode.
     *
     * @param dbPath path to the {@code copilot-agent-sessions-nitrite.db} file
     * @throws IOException if the file cannot be opened (e.g. locked by IntelliJ, or
     *                     Nitrite version mismatch)
     */
    public NitriteReader(Path dbPath) throws IOException {
        try {
            MVStoreModule storeModule = MVStoreModule.withConfig()
                    .filePath(dbPath.toString())
                    .readOnly(true)
                    .build();
            this.db = Nitrite.builder()
                    .loadModule(storeModule)
                    .openOrCreate();
        } catch (Exception e) {
            throw new IOException(buildErrorMessage(dbPath, e), e);
        }
    }

    /**
     * Walks {@code sourceDirs} (recursively) looking for files named {@value #DB_FILENAME}.
     *
     * @param sourceDirs directories to search
     * @return list of database file paths found; never null
     */
    public static List<Path> findDbFiles(List<Path> sourceDirs) {
        List<Path> found = new ArrayList<>();
        for (Path dir : sourceDirs) {
            if (!Files.isDirectory(dir)) continue;
            try {
                Files.walk(dir)
                        .filter(p -> p.getFileName().toString().equals(DB_FILENAME))
                        .forEach(found::add);
            } catch (IOException e) {
                System.err.println("Warning: cannot scan " + dir + ": " + e.getMessage());
            }
        }
        return found;
    }

    /**
     * Reads all non-deleted sessions with at least one turn from the database.
     * Sessions are returned in ascending order of creation time.
     *
     * @return list of sessions; never null
     */
    public List<AgentSession> readSessions() {
        Map<String, List<AgentTurn>> turnsBySession = readStandaloneTurns();
        NitriteMap<NitriteId, Document> sessionMap = openMap(SESSIONS_COLLECTION);
        List<AgentSession> sessions = new ArrayList<>();

        for (Document doc : sessionMap.values()) {
            AgentSession session = parseSession(doc, turnsBySession);
            if (session != null) {
                sessions.add(session);
            }
        }

        sessions.sort(Comparator.comparingLong(AgentSession::createdAtMs));
        return sessions;
    }

    // --- private helpers ---

    private Map<String, List<AgentTurn>> readStandaloneTurns() {
        Map<String, List<AgentTurn>> bySession = new HashMap<>();
        NitriteMap<NitriteId, Document> turnMap = openMap(TURNS_COLLECTION);

        for (Document doc : turnMap.values()) {
            AgentTurn turn = parseTurn(doc);
            if (turn != null) {
                bySession.computeIfAbsent(turn.sessionId(), k -> new ArrayList<>()).add(turn);
            }
        }
        return bySession;
    }

    private AgentSession parseSession(Document doc, Map<String, List<AgentTurn>> turnsBySession) {
        String id = stringField(doc, "id");
        if (id == null || id.isBlank()) return null;

        String title = stringField(doc, "name.value");
        if (title == null || title.isBlank()) title = "Agent Session";

        String user      = stringField(doc, "user");
        long createdAt   = longField(doc, "createdAt");
        long modifiedAt  = longField(doc, "modifiedAt");

        // Prefer inline turns (stored directly in the session document);
        // fall back to the standalone turns collection.
        List<AgentTurn> turns = parseInlineTurns(id, doc);
        if (turns.isEmpty()) {
            turns = turnsBySession.getOrDefault(id, List.of());
        }

        turns = turns.stream()
                .filter(t -> !t.deleted())
                .sorted(Comparator.comparingLong(AgentTurn::createdAtMs))
                .toList();

        return new AgentSession(id, title, user, createdAt, modifiedAt, turns);
    }

    private List<AgentTurn> parseInlineTurns(String sessionId, Document doc) {
        Object raw = doc.get("turns");
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();

        List<AgentTurn> turns = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Document turnDoc) {
                AgentTurn turn = parseTurnDoc(sessionId, turnDoc);
                if (turn != null) turns.add(turn);
            }
        }
        return turns;
    }

    private AgentTurn parseTurn(Document doc) {
        String sessionId = stringField(doc, "sessionId");
        if (sessionId == null) return null;
        return parseTurnDoc(sessionId, doc);
    }

    private AgentTurn parseTurnDoc(String sessionId, Document doc) {
        long    createdAt   = longField(doc, "createdAt");
        boolean deleted     = doc.get("deletedAt") != null;

        String reqString   = stringField(doc, "request.stringContent");
        String reqContents = stringField(doc, "request.contents");
        String requestText = ContentExtractor.extractText(reqString, reqContents);

        String resString   = stringField(doc, "response.stringContent");
        String resContents = stringField(doc, "response.contents");
        String responseText = ContentExtractor.extractText(resString, resContents);

        String modelName = stringField(doc, "response.modelInformation.modelName");
        String chatMode  = stringField(doc, "request.chatMode");

        return new AgentTurn(sessionId, createdAt, deleted,
                requestText, responseText, modelName, chatMode);
    }

    /**
     * Opens a raw Nitrite map by name via the store layer, bypassing the collection/repository
     * type registry. This works regardless of whether JetBrains registered the map as a plain
     * collection or an object repository.
     */
    @SuppressWarnings("unchecked")
    private NitriteMap<NitriteId, Document> openMap(String mapName) {
        NitriteMap<?, ?> raw = db.getStore().openMap(mapName, NitriteId.class, Document.class);
        return (NitriteMap<NitriteId, Document>) raw;
    }

    private String stringField(Document doc, String field) {
        try {
            Object val = doc.get(field);
            if (val instanceof String s) return s;
            if (val != null) return val.toString();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private long longField(Document doc, String field) {
        try {
            Object val = doc.get(field);
            if (val instanceof Long l)   return l;
            if (val instanceof Number n) return n.longValue();
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String buildErrorMessage(Path dbPath, Exception cause) {
        String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
        boolean versionMismatch = msg.contains("serial version") || msg.contains("InvalidClass");
        StringBuilder sb = new StringBuilder();
        sb.append("Cannot open JetBrains Copilot database at: ").append(dbPath).append("\n");
        if (versionMismatch) {
            sb.append("Possible Nitrite version mismatch: the JetBrains Copilot plugin may have\n");
            sb.append("been updated to a newer Nitrite version than this tool supports.\n");
            sb.append("Please file an issue at https://github.com/MCBoarder289/copilot-jetbrains-exporter\n");
        } else {
            sb.append("If IntelliJ/PyCharm is running, close it and try again (the DB may be locked).\n");
        }
        sb.append("Cause: ").append(msg);
        return sb.toString();
    }

    @Override
    public void close() {
        if (db != null && !db.isClosed()) {
            try {
                db.close();
            } catch (Exception ignored) {
                // Committing a read-only store throws on close — this is expected and safe to ignore.
            }
        }
    }
}
