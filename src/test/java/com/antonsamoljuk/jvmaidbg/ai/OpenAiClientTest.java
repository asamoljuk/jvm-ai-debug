package com.antonsamoljuk.jvmaidbg.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiClientTest {

    @Test
    void extractJsonStripsMarkdownFence() {
        String input = "```json\n{\"key\": \"value\"}\n```";
        assertEquals("{\"key\": \"value\"}", OpenAiClient.extractJson(input));
    }

    @Test
    void extractJsonHandlesPlainObject() {
        String input = "{\"key\": \"value\"}";
        assertEquals("{\"key\": \"value\"}", OpenAiClient.extractJson(input));
    }

    @Test
    void sanitizeRemovesTrailingEllipsisInArray() {
        String input = "{\"mentionedClasses\": [\"Foo\", \"Bar\", ...]}";
        String result = OpenAiClient.sanitizeLlmJson(input);
        assertEquals("{\"mentionedClasses\": [\"Foo\", \"Bar\"]}", result);
    }

    @Test
    void sanitizeRemovesLeadingEllipsisInArray() {
        String input = "{\"evidence\": [..., \"point one\", \"point two\"]}";
        String result = OpenAiClient.sanitizeLlmJson(input);
        assertEquals("{\"evidence\": [\"point one\", \"point two\"]}", result);
    }

    @Test
    void sanitizeRemovesMiddleEllipsisInArray() {
        String input = "{\"items\": [\"a\", ..., \"b\"]}";
        String result = OpenAiClient.sanitizeLlmJson(input);
        assertEquals("{\"items\": [\"a\", \"b\"]}", result);
    }

    @Test
    void sanitizeCollapsesEllipsisOnlyArray() {
        String input = "{\"items\": [...]}";
        String result = OpenAiClient.sanitizeLlmJson(input);
        assertEquals("{\"items\": []}", result);
    }

    @Test
    void sanitizeRemovesTrailingComma() {
        String input = "{\"key\": \"value\",}";
        String result = OpenAiClient.sanitizeLlmJson(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void sanitizeHandlesCleanJsonUnchanged() {
        String input = "{\"key\": [\"a\", \"b\"]}";
        assertEquals(input, OpenAiClient.sanitizeLlmJson(input));
    }
}
