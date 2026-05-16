package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockAiClientTest {

    private MockAiClient client;

    @BeforeEach
    void setUp() {
        client = new MockAiClient();
    }

    @Test
    void providerNameIsMock() {
        assertEquals("mock", client.getProviderName());
    }

    @Test
    void springContextFailureReturnsHighConfidence() {
        AnalysisResponse response = client.analyze(buildRequest(IssueCategory.SPRING_CONTEXT_FAILURE));

        assertEquals("HIGH", response.getConfidence());
        assertEquals(IssueCategory.SPRING_CONTEXT_FAILURE.name(), response.getDetectedIssue());
        assertNotNull(response.getLikelyRootCause());
        assertFalse(response.getLikelyRootCause().isBlank());
        assertNotNull(response.getSuggestedFixes());
        assertFalse(response.getSuggestedFixes().isEmpty());
    }

    @Test
    void nullPointerReturnsExpectedResponse() {
        AnalysisResponse response = client.analyze(buildRequest(IssueCategory.NULL_POINTER_EXCEPTION));

        assertEquals(IssueCategory.NULL_POINTER_EXCEPTION.name(), response.getDetectedIssue());
        assertFalse(response.getSuggestedFixes().isEmpty());
    }

    @Test
    void mavenBuildFailureReturnsMediumConfidence() {
        AnalysisResponse response = client.analyze(buildRequest(IssueCategory.MAVEN_BUILD_FAILURE));

        assertEquals(IssueCategory.MAVEN_BUILD_FAILURE.name(), response.getDetectedIssue());
        assertEquals("MEDIUM", response.getConfidence());
    }

    @Test
    void unknownCategoryReturnsLowConfidence() {
        AnalysisResponse response = client.analyze(buildRequest(IssueCategory.UNKNOWN));

        assertEquals(IssueCategory.UNKNOWN.name(), response.getDetectedIssue());
        assertEquals("LOW", response.getConfidence());
    }

    @ParameterizedTest
    @EnumSource(IssueCategory.class)
    void allCategoriesReturnValidResponse(IssueCategory category) {
        AnalysisResponse response = client.analyze(buildRequest(category));

        assertNotNull(response, "Response should not be null for category: " + category);
        assertNotNull(response.getDetectedIssue(), "detectedIssue should not be null");
        assertNotNull(response.getLikelyRootCause(), "likelyRootCause should not be null");
        assertNotNull(response.getSuggestedFixes(), "suggestedFixes should not be null");
        assertFalse(response.getSuggestedFixes().isEmpty(), "suggestedFixes should not be empty for: " + category);
        assertNotNull(response.getConfidence(), "confidence should not be null");
        assertNotNull(response.getEvidence(), "evidence should not be null");
    }

    @Test
    void springContextResponseUsesDetectedClassesWhenAvailable() {
        ExtractedEvidence evidence = new ExtractedEvidence();
        evidence.setClassNames(List.of("OrderService", "PaymentService"));
        DetectedIssue issue = new DetectedIssue(IssueCategory.SPRING_CONTEXT_FAILURE, "HIGH", evidence);
        AnalysisRequest request = new AnalysisRequest(issue, "prompt", "content");

        AnalysisResponse response = client.analyze(request);

        assertNotNull(response.getMentionedClasses());
        assertTrue(response.getMentionedClasses().contains("OrderService") ||
                        response.getMentionedClasses().contains("PaymentService"),
                "Should use detected class names");
    }

    private AnalysisRequest buildRequest(IssueCategory category) {
        ExtractedEvidence evidence = new ExtractedEvidence();
        DetectedIssue issue = new DetectedIssue(category, "HIGH", evidence);
        return new AnalysisRequest(issue, "test prompt", "test content");
    }
}
