package io.github.copilotjetbrains;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.mvstore.MVStoreModule;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Builds synthetic Nitrite databases for use in tests.
 *
 * <p>All sessions and turns contain realistic-looking but entirely fictional content.
 * No real conversation data is used anywhere in this class.
 */
public final class TestFixtureBuilder {

    private TestFixtureBuilder() {}

    /**
     * A fake conversation turn with request/response text and a model name.
     */
    public record FakeTurn(String requestText, String responseText, String modelName) {}

    /**
     * A fake session containing one or more turns.
     * The {@code id} must be unique within a single database.
     */
    public record FakeSession(String id, String title, String user, List<FakeTurn> turns) {}

    /**
     * Returns the standard set of fake sessions used across multiple tests.
     * Timestamps and IDs are deterministic so test assertions can be exact.
     */
    public static List<FakeSession> standardSessions() {
        return List.of(
                new FakeSession(
                        "aaaaaaaa-0001-0001-0001-000000000001",
                        "Python List Operations",
                        "testuser",
                        List.of(
                                new FakeTurn(
                                        "How do I reverse a list in Python?",
                                        "Use `list.reverse()` for in-place reversal, "
                                                + "or `list[::-1]` to return a reversed copy.",
                                        "gpt-4o"
                                ),
                                new FakeTurn(
                                        "How do I sort it in descending order?",
                                        "Use `list.sort(reverse=True)` for in-place descending sort, "
                                                + "or `sorted(lst, reverse=True)` for a new sorted list.",
                                        "gpt-4o"
                                )
                        )
                ),
                new FakeSession(
                        "bbbbbbbb-0002-0002-0002-000000000002",
                        "Java Generics Question",
                        "testuser",
                        List.of(
                                new FakeTurn(
                                        "What is type erasure in Java?",
                                        "Type erasure removes generic type parameters at compile time. "
                                                + "At runtime, `List<String>` and `List<Integer>` are both just `List`.",
                                        "gpt-4o"
                                )
                        )
                )
        );
    }

    /**
     * Creates a Nitrite database at {@code dir/test.db} populated with {@code sessions}.
     * Uses a fixed base timestamp so test assertions on {@code createdAtMs} are stable.
     *
     * @param dir      directory in which to create the database file
     * @param sessions sessions to insert
     * @return path to the created {@code test.db} file
     */
    public static Path createDb(Path dir, List<FakeSession> sessions) throws Exception {
        Path dbPath = dir.resolve("test.db");
        long baseTime = 1_700_000_000_000L; // 2023-11-14 (fixed for reproducibility)

        MVStoreModule storeModule = MVStoreModule.withConfig()
                .filePath(dbPath.toString())
                .build();

        try (Nitrite db = Nitrite.builder().loadModule(storeModule).openOrCreate()) {
            NitriteCollection sessionColl = db.getCollection(NitriteReader.SESSIONS_COLLECTION);
            NitriteCollection turnColl    = db.getCollection(NitriteReader.TURNS_COLLECTION);

            for (int si = 0; si < sessions.size(); si++) {
                FakeSession session   = sessions.get(si);
                long        sessionTs = baseTime + (long) si * 10_000L;

                sessionColl.insert(
                        Document.createDocument("id", session.id())
                                .put("name", Document.createDocument("value", session.title()))
                                .put("user", session.user())
                                .put("createdAt",  sessionTs)
                                .put("modifiedAt", sessionTs + session.turns().size() * 2_000L)
                                .put("turns",      Collections.emptyList())
                                .put("workingSet", Collections.emptyList())
                );

                for (int ti = 0; ti < session.turns().size(); ti++) {
                    FakeTurn turn   = session.turns().get(ti);
                    long     turnTs = sessionTs + (long) (ti + 1) * 2_000L;

                    turnColl.insert(
                            Document.createDocument("sessionId", session.id())
                                    .put("createdAt", turnTs)
                                    .put("request",
                                            Document.createDocument("chatMode", "agent")
                                                    .put("stringContent", turn.requestText())
                                                    .put("contents", ""))
                                    .put("response",
                                            Document.createDocument("stringContent", turn.responseText())
                                                    .put("contents", "")
                                                    .put("modelInformation",
                                                            Document.createDocument("modelName", turn.modelName())))
                    );
                }
            }
        }

        return dbPath;
    }

    /**
     * Convenience method: creates a database with {@link #standardSessions()}.
     */
    public static Path createStandardDb(Path dir) throws Exception {
        return createDb(dir, standardSessions());
    }
}
