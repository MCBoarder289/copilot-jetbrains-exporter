package io.github.copilotjetbrains.model;

/**
 * A single conversation turn: one user prompt and the assistant's response.
 *
 * @param sessionId    UUID of the parent session
 * @param createdAtMs  Unix timestamp (milliseconds) when this turn was created
 * @param deleted      True if this turn was deleted by the user; deleted turns are skipped
 * @param requestText  The user's prompt text (plain text, already extracted)
 * @param responseText The assistant's response text (plain text, already extracted)
 * @param modelName    Model used for the response (e.g. "gpt-4o"), or null if unknown
 * @param chatMode     Chat mode (e.g. "agent"), or null if unknown
 */
public record AgentTurn(
        String sessionId,
        long createdAtMs,
        boolean deleted,
        String requestText,
        String responseText,
        String modelName,
        String chatMode
) {}
