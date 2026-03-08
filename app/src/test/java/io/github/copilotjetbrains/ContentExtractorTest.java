package io.github.copilotjetbrains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTest {

    // --- extractText: stringContent preferred ---

    @Test
    void extractText_prefersStringContentOverContentsJson() {
        String result = ContentExtractor.extractText("plain text", buildMarkdownContentsJson("from json"));
        assertThat(result).isEqualTo("plain text");
    }

    @Test
    void extractText_stripsLeadingAndTrailingWhitespace() {
        assertThat(ContentExtractor.extractText("  trimmed  ", null)).isEqualTo("trimmed");
    }

    @Test
    void extractText_returnsEmptyWhenBothNull() {
        assertThat(ContentExtractor.extractText(null, null)).isEmpty();
    }

    @Test
    void extractText_returnsEmptyWhenBothBlank() {
        assertThat(ContentExtractor.extractText("   ", "   ")).isEmpty();
    }

    // --- extractText: contents fallback ---

    @Test
    void extractText_fallsBackToContentsJsonWhenStringContentBlank() {
        String result = ContentExtractor.extractText(null, buildMarkdownContentsJson("Extracted from contents"));
        assertThat(result).isEqualTo("Extracted from contents");
    }

    @Test
    void extractText_returnsEmptyWhenContentsJsonIsMalformed() {
        assertThat(ContentExtractor.extractText(null, "{not valid json at all")).isEmpty();
    }

    @Test
    void extractText_returnsEmptyWhenContentsJsonIsNotAnObject() {
        // A JSON array is not a valid contents structure
        assertThat(ContentExtractor.extractText(null, "[1, 2, 3]")).isEmpty();
    }

    // --- parseContentsJson ---

    @Test
    void parseContentsJson_extractsMarkdownText() throws Exception {
        String json = buildMarkdownContentsJson("The response text here");
        assertThat(ContentExtractor.parseContentsJson(json)).isEqualTo("The response text here");
    }

    @Test
    void parseContentsJson_handlesSpecialCharactersInText() throws Exception {
        String text = "Use `list.sort()` for sorting & other things.";
        assertThat(ContentExtractor.parseContentsJson(buildMarkdownContentsJson(text))).isEqualTo(text);
    }

    @Test
    void parseContentsJson_returnsEmptyForEmptyObject() throws Exception {
        assertThat(ContentExtractor.parseContentsJson("{}")).isEmpty();
    }

    // --- expandStringifiedValues ---

    @Test
    void expandStringifiedValues_expandsStringifiedJsonObject() throws Exception {
        String raw = "{\"key\":{\"value\":\"{\\\"inner\\\":true}\"}}";
        com.fasterxml.jackson.databind.JsonNode expanded =
                ContentExtractor.expandStringifiedValues(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw));
        assertThat(expanded.path("key").path("value").path("inner").asBoolean()).isTrue();
    }

    @Test
    void expandStringifiedValues_leavesNonJsonStringsUnchanged() throws Exception {
        String raw = "{\"key\":{\"value\":\"just a plain string\"}}";
        com.fasterxml.jackson.databind.JsonNode expanded =
                ContentExtractor.expandStringifiedValues(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw));
        assertThat(expanded.path("key").path("value").asText()).isEqualTo("just a plain string");
    }

    // --- helper ---

    /**
     * Builds a minimal nested contents JSON that mirrors the JetBrains structure:
     * outer object → Subgraph entry → Value entry → Markdown node → text.
     *
     * <p>Each layer is stringified so that {@link ContentExtractor#expandStringifiedValues}
     * must recursively parse them before the text becomes accessible.
     */
    static String buildMarkdownContentsJson(String text) {
        // Layer 1 (innermost): {"text": "<text>"}
        String dataJson   = "{\"text\":\"" + escapeJson(text) + "\"}";
        // Layer 2: {"type":"Markdown","data":"<layer1>"}
        String markdownJson = "{\"type\":\"Markdown\",\"data\":\"" + escapeJson(dataJson) + "\"}";
        // Layer 3: {"type":"Value","value":"<layer2>"}
        String valueJson  = "{\"type\":\"Value\",\"value\":\"" + escapeJson(markdownJson) + "\"}";
        // Layer 4 (subgraph value): {"inner-key":<layer3>}  (NOT stringified — already an object)
        String subgraphValueJson = "{\"inner-key\":" + valueJson + "}";
        // Layer 5: {"type":"Subgraph","value":"<layer4>"}
        String subgraphJson = "{\"type\":\"Subgraph\",\"value\":\"" + escapeJson(subgraphValueJson) + "\"}";
        // Outer: {"outer-key":<layer5>}
        return "{\"outer-key\":" + subgraphJson + "}";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
