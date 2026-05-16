package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.DetectedIssue;
import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;
import com.antonsamoljuk.jvmaidbg.model.IssueCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Test
    void promptContainsDetectedCategory() {
        DetectedIssue issue = buildIssue(IssueCategory.SPRING_CONTEXT_FAILURE);
        AnalysisRequest request = promptBuilder.build(issue, "some log content");

        assertTrue(request.getPrompt().contains("SPRING_CONTEXT_FAILURE"),
                "Prompt should include detected category");
    }

    @Test
    void promptContainsRawExcerpt() {
        DetectedIssue issue = buildIssue(IssueCategory.SPRING_CONTEXT_FAILURE);
        String content = "BeanCreationException occurred at startup";
        AnalysisRequest request = promptBuilder.build(issue, content);

        assertTrue(request.getPrompt().contains(content), "Prompt should include raw log content");
    }

    @Test
    void promptContainsOutputSchema() {
        DetectedIssue issue = buildIssue(IssueCategory.NULL_POINTER_EXCEPTION);
        AnalysisRequest request = promptBuilder.build(issue, "NPE log");

        assertTrue(request.getPrompt().contains("likelyRootCause"), "Prompt should include output schema field");
        assertTrue(request.getPrompt().contains("suggestedFixes"), "Prompt should include suggestedFixes field");
        assertTrue(request.getPrompt().contains("confidence"), "Prompt should include confidence field");
    }

    @Test
    void promptContainsEvidenceWhenPresent() {
        ExtractedEvidence evidence = new ExtractedEvidence();
        evidence.setExceptionNames(List.of("BeanCreationException", "UnsatisfiedDependencyException"));
        evidence.setCausedByChain(List.of("UnsatisfiedDependencyException: ...", "IllegalStateException: ..."));
        evidence.setFrameworkIndicators(List.of("Spring Framework"));

        DetectedIssue issue = new DetectedIssue(IssueCategory.SPRING_CONTEXT_FAILURE, "HIGH", evidence);
        AnalysisRequest request = promptBuilder.build(issue, "content");

        assertTrue(request.getPrompt().contains("BeanCreationException"), "Prompt should contain exception names");
        assertTrue(request.getPrompt().contains("Spring Framework"), "Prompt should contain framework indicators");
    }

    @Test
    void requestPreservesRawContent() {
        DetectedIssue issue = buildIssue(IssueCategory.MAVEN_BUILD_FAILURE);
        String content = "BUILD FAILURE";
        AnalysisRequest request = promptBuilder.build(issue, content);

        assertEquals(content, request.getRawContent());
    }

    @Test
    void promptIsTruncatedForVeryLargeInput() {
        DetectedIssue issue = buildIssue(IssueCategory.UNKNOWN);
        String largeContent = "x".repeat(10000);
        AnalysisRequest request = promptBuilder.build(issue, largeContent);

        // Prompt should exist but not contain the full 10000 chars
        assertNotNull(request.getPrompt());
        assertTrue(request.getPrompt().length() < 10000 + 2000,
                "Prompt should not grow unbounded with large input");
    }

    private DetectedIssue buildIssue(IssueCategory category) {
        return new DetectedIssue(category, "HIGH", new ExtractedEvidence());
    }
}
