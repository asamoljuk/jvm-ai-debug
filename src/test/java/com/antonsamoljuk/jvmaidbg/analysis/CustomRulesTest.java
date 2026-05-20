package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.DetectedIssue;
import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;
import com.antonsamoljuk.jvmaidbg.model.IssueCategory;
import com.antonsamoljuk.jvmaidbg.parser.LogParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomRulesTest {

    @Test
    void emptyRulesMatchNothing() {
        assertNull(CustomRules.empty().match("anything goes here"));
    }

    @Test
    void matchesSubstringPattern() {
        CustomRules rules = new CustomRules(List.of(
                new CustomRules.Rule(IssueCategory.SPRING_CONTEXT_FAILURE,
                        List.of("com.mycompany.framework"))));
        assertEquals(IssueCategory.SPRING_CONTEXT_FAILURE,
                rules.match("Exception in com.mycompany.framework.Bootstrap"));
    }

    @Test
    void matchIsCaseInsensitive() {
        CustomRules rules = new CustomRules(List.of(
                new CustomRules.Rule(IssueCategory.HIBERNATE_MAPPING_ERROR,
                        List.of("MyCompanyORM"))));
        assertEquals(IssueCategory.HIBERNATE_MAPPING_ERROR,
                rules.match("at MYCOMPANYORM.Session.flush(Session.java:42)"));
    }

    @Test
    void firstMatchingRuleWins() {
        CustomRules rules = new CustomRules(List.of(
                new CustomRules.Rule(IssueCategory.SPRING_CONTEXT_FAILURE, List.of("alpha")),
                new CustomRules.Rule(IssueCategory.HIBERNATE_MAPPING_ERROR, List.of("alpha"))));
        assertEquals(IssueCategory.SPRING_CONTEXT_FAILURE, rules.match("alpha beta"));
    }

    @Test
    void loadFromFile(@TempDir Path tempDir) throws IOException {
        Path rulesPath = tempDir.resolve("rules.json");
        Files.writeString(rulesPath, """
                {
                  "rules": [
                    {
                      "category": "SPRING_CONTEXT_FAILURE",
                      "patterns": ["com.mycorp.bootstrap"]
                    }
                  ]
                }
                """);
        CustomRules rules = CustomRules.load(rulesPath);
        assertEquals(1, rules.size());
        assertEquals(IssueCategory.SPRING_CONTEXT_FAILURE,
                rules.match("at com.mycorp.bootstrap.Init.start"));
    }

    @Test
    void loadFromMissingFileReturnsEmpty(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nope.json");
        CustomRules rules = CustomRules.load(missing);
        assertEquals(0, rules.size());
    }

    @Test
    void unknownCategoryInFileIsSkipped(@TempDir Path tempDir) throws IOException {
        Path rulesPath = tempDir.resolve("rules.json");
        Files.writeString(rulesPath, """
                {
                  "rules": [
                    { "category": "NOT_A_REAL_CATEGORY", "patterns": ["foo"] },
                    { "category": "JVM_MEMORY_ERROR", "patterns": ["bar"] }
                  ]
                }
                """);
        CustomRules rules = CustomRules.load(rulesPath);
        assertEquals(1, rules.size());
        assertEquals(IssueCategory.JVM_MEMORY_ERROR, rules.match("contains bar somewhere"));
    }

    @Test
    void customRuleOverridesBuiltInDetection() {
        // Content would normally classify as NULL_POINTER_EXCEPTION, but custom rule maps it to HIBERNATE
        String content = "java.lang.NullPointerException at com.acme.persistence.Tx.commit(Tx.java:18)";
        CustomRules rules = new CustomRules(List.of(
                new CustomRules.Rule(IssueCategory.HIBERNATE_MAPPING_ERROR,
                        List.of("com.acme.persistence"))));
        IssueDetector detector = new IssueDetector(rules);
        ExtractedEvidence evidence = new LogParser().parse(content);
        DetectedIssue issue = detector.detect(evidence, content);
        assertEquals(IssueCategory.HIBERNATE_MAPPING_ERROR, issue.getCategory());
    }
}
