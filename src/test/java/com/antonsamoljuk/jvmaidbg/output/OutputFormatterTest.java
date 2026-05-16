package com.antonsamoljuk.jvmaidbg.output;

import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.antonsamoljuk.jvmaidbg.model.IssueCategory;
import com.antonsamoljuk.jvmaidbg.model.OutputFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputFormatterTest {

    private OutputFormatter formatter;
    private AnalysisResponse sampleResponse;

    @BeforeEach
    void setUp() {
        formatter = new OutputFormatter();
        sampleResponse = new AnalysisResponse(
                IssueCategory.SPRING_CONTEXT_FAILURE.name(),
                "Spring application context failed to start",
                "A circular dependency between UserService and NotificationService.",
                List.of("BeanCreationException found", "Circular dependency detected"),
                List.of("Refactor dependency direction", "Introduce interface boundary"),
                "HIGH",
                List.of("UserService", "NotificationService")
        );
    }

    @Test
    void textFormatContainsHeader() {
        String output = formatter.format(sampleResponse, OutputFormat.TEXT);
        assertTrue(output.contains("=== AI JVM Debug Assistant ==="), "Should contain header");
    }

    @Test
    void textFormatContainsDetectedIssue() {
        String output = formatter.formatText(sampleResponse);
        assertTrue(output.contains("Detected issue:"), "Should contain detected issue label");
    }

    @Test
    void textFormatContainsLikelyRootCause() {
        String output = formatter.formatText(sampleResponse);
        assertTrue(output.contains("Likely root cause:"), "Should contain root cause label");
        assertTrue(output.contains("circular dependency"), "Should contain root cause text");
    }

    @Test
    void textFormatContainsSuggestedFixes() {
        String output = formatter.formatText(sampleResponse);
        assertTrue(output.contains("Suggested fixes:"), "Should contain fixes section");
        assertTrue(output.contains("1."), "Should number the fixes");
        assertTrue(output.contains("Refactor dependency direction"), "Should contain fix text");
    }

    @Test
    void textFormatContainsConfidence() {
        String output = formatter.formatText(sampleResponse);
        assertTrue(output.contains("Confidence:"), "Should contain confidence label");
        assertTrue(output.contains("High"), "Should contain confidence value");
    }

    @Test
    void textFormatContainsClassesMentioned() {
        String output = formatter.formatText(sampleResponse);
        assertTrue(output.contains("Files/classes mentioned:"), "Should contain classes section");
        assertTrue(output.contains("UserService"), "Should list UserService");
        assertTrue(output.contains("NotificationService"), "Should list NotificationService");
    }

    @Test
    void jsonFormatIsValidJson() throws Exception {
        String output = formatter.format(sampleResponse, OutputFormat.JSON);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(output);
        assertNotNull(node, "Output should be valid JSON");
    }

    @Test
    void jsonFormatContainsAllRequiredFields() throws Exception {
        String output = formatter.formatJson(sampleResponse);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(output);

        assertTrue(node.has("detectedIssue"), "JSON should have detectedIssue");
        assertTrue(node.has("title"), "JSON should have title");
        assertTrue(node.has("likelyRootCause"), "JSON should have likelyRootCause");
        assertTrue(node.has("evidence"), "JSON should have evidence");
        assertTrue(node.has("suggestedFixes"), "JSON should have suggestedFixes");
        assertTrue(node.has("confidence"), "JSON should have confidence");
        assertTrue(node.has("mentionedClasses"), "JSON should have mentionedClasses");
    }

    @Test
    void jsonFormatDetectedIssueMatchesCategory() throws Exception {
        String output = formatter.formatJson(sampleResponse);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(output);

        assertEquals(IssueCategory.SPRING_CONTEXT_FAILURE.name(),
                node.get("detectedIssue").asText());
    }

    @Test
    void jsonEvidenceIsArray() throws Exception {
        String output = formatter.formatJson(sampleResponse);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(output);

        assertTrue(node.get("evidence").isArray(), "evidence should be a JSON array");
        assertEquals(2, node.get("evidence").size());
    }

    @Test
    void textFormatHandlesNullFields() {
        AnalysisResponse sparse = new AnalysisResponse();
        sparse.setDetectedIssue("UNKNOWN");
        sparse.setLikelyRootCause("No information");

        assertDoesNotThrow(() -> formatter.formatText(sparse),
                "Should handle null lists gracefully");
    }
}
