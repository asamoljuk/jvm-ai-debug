package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.DetectedIssue;
import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;
import com.antonsamoljuk.jvmaidbg.model.IssueCategory;
import com.antonsamoljuk.jvmaidbg.parser.LogParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class IssueDetectorTest {

    private IssueDetector detector;
    private LogParser parser;

    @BeforeEach
    void setUp() {
        detector = new IssueDetector();
        parser = new LogParser();
    }

    @Test
    void detectsSpringContextFailure() throws Exception {
        String content = loadSample("spring-bean-failure.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.SPRING_CONTEXT_FAILURE, issue.getCategory());
        assertEquals("HIGH", issue.getConfidence());
    }

    @Test
    void detectsNullPointerException() throws Exception {
        String content = loadSample("null-pointer.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.NULL_POINTER_EXCEPTION, issue.getCategory());
    }

    @Test
    void detectsMavenBuildFailure() throws Exception {
        String content = loadSample("maven-build-failure.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.MAVEN_BUILD_FAILURE, issue.getCategory());
    }

    @Test
    void detectsJunitFailure() throws Exception {
        String content = loadSample("junit-failure.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.JUNIT_TEST_FAILURE, issue.getCategory());
    }

    @Test
    void detectsGradleBuildFailure() throws Exception {
        String content = loadSample("gradle-build-failure.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.GRADLE_BUILD_FAILURE, issue.getCategory());
    }

    @Test
    void detectsTestNGFailure() throws Exception {
        String content = loadSample("testng-failure.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.TESTNG_TEST_FAILURE, issue.getCategory());
    }

    @Test
    void detectsHibernateError() throws Exception {
        String content = loadSample("hibernate-error.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.HIBERNATE_MAPPING_ERROR, issue.getCategory());
    }

    @Test
    void detectsStackOverflow() throws Exception {
        String content = loadSample("stack-overflow.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.JVM_MEMORY_ERROR, issue.getCategory());
    }

    @Test
    void detectsNoClassDefFound() throws Exception {
        String content = loadSample("no-class-def-found.txt");
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        // Outer error wins over the caused-by ClassNotFoundException
        assertEquals(IssueCategory.NO_CLASS_DEF_FOUND, issue.getCategory());
    }

    @Test
    void detectsClassNotFoundException() {
        String content = "java.lang.ClassNotFoundException: com.example.MissingClass\n" +
                "Caused by: java.lang.ClassNotFoundException: com.example.MissingClass\n" +
                "\tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:520)";
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.CLASS_NOT_FOUND, issue.getCategory());
    }

    @Test
    void detectsOutOfMemoryError() {
        String content = "java.lang.OutOfMemoryError: Java heap space\n" +
                "\tat java.util.Arrays.copyOf(Arrays.java:3210)\n" +
                "\tat com.example.app.DataProcessor.process(DataProcessor.java:55)";
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.JVM_MEMORY_ERROR, issue.getCategory());
    }

    @Test
    void returnsUnknownForUnrecognisedContent() {
        String content = "Something went wrong in the application but no useful info.";
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals(IssueCategory.UNKNOWN, issue.getCategory());
        assertEquals("LOW", issue.getConfidence());
    }

    @Test
    void confidenceIsHighWhenExceptionsAndCausedByPresent() {
        String content = "java.lang.NullPointerException: null\n" +
                "\tat com.example.Service.method(Service.java:10)\n" +
                "Caused by: java.lang.IllegalArgumentException: bad arg";
        ExtractedEvidence evidence = parser.parse(content);
        DetectedIssue issue = detector.detect(evidence, content);

        assertEquals("HIGH", issue.getConfidence());
    }

    private String loadSample(String filename) throws IOException, URISyntaxException {
        Path path = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource("samples/" + filename)).toURI());
        return Files.readString(path);
    }
}
