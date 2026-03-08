package io.github.copilotjetbrains;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.List;

/**
 * Extracts plain text from JetBrains Copilot turn content.
 *
 * <p>Each turn stores content in two fields:
 * <ul>
 *   <li>{@code stringContent} — plain text (preferred, usually populated for simple chats)</li>
 *   <li>{@code contents} — a deeply nested JSON-in-JSON structure used for agent responses
 *       that include steps, references, and inline code blocks</li>
 * </ul>
 *
 * <p>The {@code contents} field nests JSON strings inside JSON strings (up to 3 levels deep).
 * The structure is: outer object → Subgraph entry → Value entry → Markdown entry → text.
 * This class handles the recursive expansion before traversal.
 */
public final class ContentExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContentExtractor() {}

    /**
     * Extracts readable text from a turn's request or response.
     *
     * <p>Uses {@code stringContent} as the primary source. If it is blank or null, falls back
     * to parsing the nested {@code contents} JSON field.
     *
     * @param stringContent plain text content field (may be null or blank)
     * @param contentsJson  nested JSON-in-JSON content field (may be null or blank)
     * @return extracted text, or an empty string if nothing could be extracted
     */
    public static String extractText(String stringContent, String contentsJson) {
        if (stringContent != null && !stringContent.isBlank()) {
            return stringContent.strip();
        }
        if (contentsJson == null || contentsJson.isBlank() || !contentsJson.startsWith("{")) {
            return "";
        }
        try {
            return parseContentsJson(contentsJson);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Parses the deeply nested {@code contents} JSON field and returns the Markdown text.
     * Exposed package-private for unit testing.
     */
    static String parseContentsJson(String json) throws Exception {
        JsonNode expanded = expandStringifiedValues(MAPPER.readTree(json));
        return findMarkdownText(expanded);
    }

    /**
     * Recursively expands any {@code "value"} or {@code "data"} fields whose string value
     * is itself a JSON object or array. This "un-stringifies" the nested JSON layers that
     * JetBrains encodes when storing agent response content.
     */
    static JsonNode expandStringifiedValues(JsonNode node) throws Exception {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fields = obj.fieldNames();
            // Collect field names first to avoid ConcurrentModificationException
            List<String> names = new java.util.ArrayList<>();
            fields.forEachRemaining(names::add);
            for (String field : names) {
                JsonNode child = obj.get(field);
                if ((field.equals("value") || field.equals("data")) && child.isTextual()) {
                    String text = child.asText();
                    if (text.startsWith("{") || text.startsWith("[")) {
                        try {
                            JsonNode parsed = MAPPER.readTree(text);
                            if (parsed.isArray()) {
                                ArrayNode arr = MAPPER.createArrayNode();
                                for (JsonNode elem : parsed) {
                                    arr.add(expandStringifiedValues(elem));
                                }
                                obj.set(field, arr);
                            } else {
                                obj.set(field, expandStringifiedValues(parsed));
                            }
                        } catch (Exception ignored) {
                            // Not parseable as JSON — leave as-is.
                        }
                    }
                } else {
                    obj.set(field, expandStringifiedValues(child));
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, expandStringifiedValues(arr.get(i)));
            }
        }
        return node;
    }

    /**
     * Traverses the expanded contents tree looking for a Markdown text value.
     * Handles the Subgraph → Value → Markdown path used by the JetBrains plugin.
     */
    private static String findMarkdownText(JsonNode root) {
        if (!root.isObject()) return "";

        for (JsonNode entry : root) {
            String type = entry.path("type").asText();

            if ("Subgraph".equals(type)) {
                JsonNode subgraphValue = entry.path("value");
                String text = findValueInSubgraph(subgraphValue);
                if (text != null && !text.isBlank()) return text;
            }

            // Also check Value nodes at the top level (seen in some older plugin versions).
            if ("Value".equals(type)) {
                String text = extractFromValueNode(entry.path("value"));
                if (text != null && !text.isBlank()) return text;
            }
        }

        return "";
    }

    private static String findValueInSubgraph(JsonNode subgraph) {
        if (!subgraph.isObject()) return null;
        for (JsonNode entry : subgraph) {
            String type = entry.path("type").asText();
            if ("Value".equals(type)) {
                String text = extractFromValueNode(entry.path("value"));
                if (text != null && !text.isBlank()) return text;
            }
        }
        return null;
    }

    private static String extractFromValueNode(JsonNode valueNode) {
        if (!valueNode.isObject()) return null;
        String type = valueNode.path("type").asText();
        if (!"Markdown".equals(type)) return null;

        JsonNode data = valueNode.path("data");
        if (data.isObject()) {
            return data.path("text").asText(null);
        }
        if (data.isTextual()) {
            return data.asText();
        }
        return null;
    }
}
