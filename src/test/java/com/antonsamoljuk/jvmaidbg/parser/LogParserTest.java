package com.antonsamoljuk.jvmaidbg.parser;

import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class LogParserTest {

    private LogParser parser;

    @BeforeEach
    void setUp() {
        parser = new LogParser();
    }

    @Test
    void extractsExceptionNamesFromSpringTrace() throws Exception {
        String content = loadSample("spring-bean-failure.txt");
        List<String> exceptions = parser.extractExceptionNames(content);

        assertTrue(exceptions.contains("BeanCreationException"), "Should detect BeanCreationException");
        assertTrue(exceptions.contains("UnsatisfiedDependencyException"), "Should detect UnsatisfiedDependencyException");
        assertTrue(exceptions.contains("IllegalStateException"), "Should detect IllegalStateException");
    }

    @Test
    void extractsCausedByChain() throws Exception {
        String content = loadSample("spring-bean-failure.txt");
        List<String> chain = parser.extractCausedByChain(content);

        assertFalse(chain.isEmpty(), "Caused-by chain should not be empty");
        assertTrue(chain.stream().anyMatch(c -> c.contains("UnsatisfiedDependencyException")),
                "Chain should contain UnsatisfiedDependencyException");
    }

    @Test
    void extractsClassNamesFromStackFrames() throws Exception {
        String content = loadSample("spring-bean-failure.txt");
        List<String> classes = parser.extractClassNames(content);

        assertFalse(classes.isEmpty(), "Should extract at least one class name");
    }

    @Test
    void detectsSpringFrameworkIndicator() throws Exception {
        String content = loadSample("spring-bean-failure.txt");
        List<String> indicators = parser.detectFrameworkIndicators(content);

        assertTrue(indicators.contains("Spring Framework"), "Should detect Spring Framework");
    }

    @Test
    void detectsMavenBuildToolIndicator() throws Exception {
        String content = loadSample("maven-build-failure.txt");
        List<String> indicators = parser.detectBuildToolIndicators(content);

        assertTrue(indicators.contains("Maven"), "Should detect Maven");
    }

    @Test
    void detectsJunitTestFramework() throws Exception {
        String content = loadSample("junit-failure.txt");
        List<String> indicators = parser.detectTestFrameworkIndicators(content);

        assertTrue(indicators.contains("JUnit"), "Should detect JUnit");
    }

    @Test
    void extractsNpeFromNullPointerTrace() throws Exception {
        String content = loadSample("null-pointer.txt");
        List<String> exceptions = parser.extractExceptionNames(content);

        assertTrue(exceptions.contains("NullPointerException"), "Should detect NullPointerException");
    }

    @Test
    void parsesFullSpringTrace() throws Exception {
        String content = loadSample("spring-bean-failure.txt");
        ExtractedEvidence evidence = parser.parse(content);

        assertNotNull(evidence);
        assertFalse(evidence.getExceptionNames().isEmpty());
        assertFalse(evidence.getCausedByChain().isEmpty());
        assertNotNull(evidence.getRawExcerpt());
        assertFalse(evidence.getRawExcerpt().isBlank());
    }

    @Test
    void rawExcerptIsTruncatedForLargeInput() {
        String large = "a".repeat(5000);
        ExtractedEvidence evidence = parser.parse(large);
        assertTrue(evidence.getRawExcerpt().length() < 5000, "Excerpt should be truncated");
    }

    private String loadSample(String filename) throws IOException, URISyntaxException {
        Path path = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource("samples/" + filename)).toURI());
        return Files.readString(path);
    }
}
