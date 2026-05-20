package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.DetectedIssue;
import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;
import com.antonsamoljuk.jvmaidbg.model.IssueCategory;

import java.util.List;

public class IssueDetector {

    private final CustomRules customRules;

    public IssueDetector() {
        this(CustomRules.empty());
    }

    public IssueDetector(CustomRules customRules) {
        this.customRules = customRules;
    }

    public DetectedIssue detect(ExtractedEvidence evidence, String rawContent) {
        String lower = rawContent.toLowerCase();
        List<String> exceptions = evidence.getExceptionNames();
        List<String> causedBy = evidence.getCausedByChain();

        // User-defined rules take precedence over built-in heuristics.
        IssueCategory custom = customRules.match(rawContent);
        IssueCategory category = (custom != null) ? custom : categorize(exceptions, causedBy, lower, evidence);
        String confidence = determineConfidence(category, exceptions, causedBy, lower);

        return new DetectedIssue(category, confidence, evidence);
    }

    private IssueCategory categorize(List<String> exceptions, List<String> causedBy,
                                     String lower, ExtractedEvidence evidence) {
        // Spring context failures
        if (containsAny(exceptions, "BeanCreationException", "UnsatisfiedDependencyException",
                "BeanDefinitionParsingException", "NoSuchBeanDefinitionException") ||
                lower.contains("applicationcontext") && lower.contains("fail")) {
            return IssueCategory.SPRING_CONTEXT_FAILURE;
        }

        // JVM memory errors (check before generic exceptions)
        if (containsAny(exceptions, "OutOfMemoryError", "StackOverflowError", "GCOverheadLimitExceededError")) {
            return IssueCategory.JVM_MEMORY_ERROR;
        }

        // Class loading errors — check NoClassDefFoundError first since it usually has
        // "Caused by: ClassNotFoundException" inside it. The outer error is the true category.
        if (exceptions.contains("NoClassDefFoundError")) {
            return IssueCategory.NO_CLASS_DEF_FOUND;
        }
        if (exceptions.contains("ClassNotFoundException")) {
            return IssueCategory.CLASS_NOT_FOUND;
        }

        // NullPointerException
        if (exceptions.contains("NullPointerException")) {
            return IssueCategory.NULL_POINTER_EXCEPTION;
        }

        // Hibernate/JPA
        if (containsAny(exceptions, "MappingException", "HibernateException",
                "PersistenceException", "EntityNotFoundException",
                "org.hibernate", "javax.persistence", "jakarta.persistence") ||
                lower.contains("hibernate") && (lower.contains("mapping") || lower.contains("schema"))) {
            return IssueCategory.HIBERNATE_MAPPING_ERROR;
        }

        // TestNG (check before JUnit — TestNG also throws AssertionError, but "org.testng" is more specific)
        if (lower.contains("org.testng") || evidence.getTestFrameworkIndicators().contains("TestNG")) {
            return IssueCategory.TESTNG_TEST_FAILURE;
        }

        // JUnit test failure (check before Maven — JUnit failures often appear inside Maven output)
        if (lower.contains("org.junit") || lower.contains("assertionerror") ||
                lower.contains("tests run:") || evidence.getTestFrameworkIndicators().contains("JUnit")) {
            return IssueCategory.JUNIT_TEST_FAILURE;
        }

        // Maven build failure
        if (lower.contains("build failure") || lower.contains("[error]") ||
                lower.contains("compilation failure") || lower.contains("maven")) {
            if (!evidence.getBuildToolIndicators().contains("Gradle")) {
                return IssueCategory.MAVEN_BUILD_FAILURE;
            }
        }

        // Gradle build failure
        if (lower.contains("task :") || lower.contains("build failed") ||
                lower.contains("gradle") || evidence.getBuildToolIndicators().contains("Gradle")) {
            return IssueCategory.GRADLE_BUILD_FAILURE;
        }

        return IssueCategory.UNKNOWN;
    }

    private String determineConfidence(IssueCategory category, List<String> exceptions,
                                       List<String> causedBy, String lower) {
        if (category == IssueCategory.UNKNOWN) {
            return "LOW";
        }

        boolean hasExceptions = !exceptions.isEmpty();
        boolean hasCausedByChain = !causedBy.isEmpty();

        if (hasExceptions && hasCausedByChain) {
            return "HIGH";
        }
        if (hasExceptions || hasCausedByChain) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean containsAny(List<String> list, String... values) {
        for (String v : values) {
            for (String item : list) {
                if (item.contains(v)) return true;
            }
        }
        return false;
    }
}
