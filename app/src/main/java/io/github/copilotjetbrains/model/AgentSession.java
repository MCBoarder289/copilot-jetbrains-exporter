package io.github.copilotjetbrains.model;

import java.util.List;

/**
 * A single JetBrains Copilot agent chat session with all its turns.
 *
 * @param id          UUID of the session (from the JetBrains Nitrite DB)
 * @param title       Human-readable title set by JetBrains
 * @param user        JetBrains username of the session owner
 * @param createdAtMs Unix timestamp (milliseconds) when the session was created
 * @param modifiedAtMs Unix timestamp (milliseconds) when the session was last modified
 * @param turns       Ordered list of conversation turns (user prompt + assistant response)
 */
public record AgentSession(
        String id,
        String title,
        String user,
        long createdAtMs,
        long modifiedAtMs,
        List<AgentTurn> turns
) {}
