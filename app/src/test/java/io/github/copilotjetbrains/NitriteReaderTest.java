package io.github.copilotjetbrains;

import io.github.copilotjetbrains.model.AgentSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NitriteReaderTest {

    @TempDir
    Path tempDir;

    // --- session count ---

    @Test
    void readSessions_returnsAllSessions() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            List<AgentSession> sessions = reader.readSessions();
            assertThat(sessions).hasSize(TestFixtureBuilder.standardSessions().size());
        }
    }

    // --- session metadata ---

    @Test
    void readSessions_sessionHasCorrectTitle() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            List<AgentSession> sessions = reader.readSessions();
            assertThat(sessions).anyMatch(s -> s.title().equals("Python List Operations"));
        }
    }

    @Test
    void readSessions_sessionHasCorrectUser() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            List<AgentSession> sessions = reader.readSessions();
            assertThat(sessions).allMatch(s -> "testuser".equals(s.user()));
        }
    }

    @Test
    void readSessions_sessionsOrderedByCreationTime() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            List<AgentSession> sessions = reader.readSessions();
            for (int i = 1; i < sessions.size(); i++) {
                assertThat(sessions.get(i).createdAtMs())
                        .isGreaterThanOrEqualTo(sessions.get(i - 1).createdAtMs());
            }
        }
    }

    // --- turns ---

    @Test
    void readSessions_turnsArePopulated() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            AgentSession session = reader.readSessions().stream()
                    .filter(s -> s.title().equals("Python List Operations"))
                    .findFirst().orElseThrow();
            assertThat(session.turns()).hasSize(2);
        }
    }

    @Test
    void readSessions_firstTurnHasCorrectRequestText() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            AgentSession session = reader.readSessions().stream()
                    .filter(s -> s.title().equals("Python List Operations"))
                    .findFirst().orElseThrow();
            assertThat(session.turns().get(0).requestText())
                    .isEqualTo("How do I reverse a list in Python?");
        }
    }

    @Test
    void readSessions_turnHasNonBlankResponseText() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            AgentSession session = reader.readSessions().stream()
                    .filter(s -> s.title().equals("Python List Operations"))
                    .findFirst().orElseThrow();
            assertThat(session.turns().get(0).responseText()).isNotBlank();
        }
    }

    @Test
    void readSessions_turnHasModelName() throws Exception {
        Path dbPath = TestFixtureBuilder.createStandardDb(tempDir);
        try (NitriteReader reader = new NitriteReader(dbPath)) {
            AgentSession session = reader.readSessions().stream()
                    .filter(s -> s.title().equals("Python List Operations"))
                    .findFirst().orElseThrow();
            assertThat(session.turns().get(0).modelName()).isEqualTo("gpt-4o");
        }
    }

    // --- file discovery ---

    @Test
    void findDbFiles_findsFileInSubdirectory() throws Exception {
        Path subDir = tempDir.resolve("jetbrains").resolve("profiles");
        Files.createDirectories(subDir);
        Path testDb = TestFixtureBuilder.createStandardDb(subDir);
        Path renamedDb = subDir.resolve(NitriteReader.DB_FILENAME);
        Files.move(testDb, renamedDb);

        List<Path> found = NitriteReader.findDbFiles(List.of(tempDir.resolve("jetbrains")));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getFileName().toString()).isEqualTo(NitriteReader.DB_FILENAME);
    }

    @Test
    void findDbFiles_returnsEmptyForMissingDirectory() {
        List<Path> found = NitriteReader.findDbFiles(List.of(tempDir.resolve("nonexistent")));
        assertThat(found).isEmpty();
    }

    // --- error handling ---

    @Test
    void constructor_throwsIOExceptionForNonExistentFile() {
        assertThatThrownBy(() -> new NitriteReader(tempDir.resolve("missing.db")))
                .isInstanceOf(java.io.IOException.class);
    }
}
