package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.IssueCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User-defined detection rules loaded from a JSON file. Each rule maps a list of substring
 * patterns to a built-in {@link IssueCategory}. Rules are evaluated before built-in detection,
 * so they can override the default categorization for project-specific frameworks.
 *
 * <p>Example rules.json:
 * <pre>
 * {
 *   "rules": [
 *     { "category": "SPRING_CONTEXT_FAILURE",
 *       "patterns": ["com.mycompany.springwrapper", "MyAppContextException"] }
 *   ]
 * }
 * </pre>
 */
public final class CustomRules {

    public static final Path DEFAULT_PATH =
            Path.of(System.getProperty("user.home"), ".jvm-ai-debug", "rules.json");

    private final List<Rule> rules;

    public CustomRules(List<Rule> rules) {
        this.rules = Collections.unmodifiableList(rules);
    }

    public static CustomRules empty() {
        return new CustomRules(List.of());
    }

    /** Loads rules from the given file. Returns {@link #empty()} if the file does not exist. */
    public static CustomRules load(Path path) {
        if (path == null || !Files.exists(path)) return empty();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(path.toFile());
            JsonNode rulesNode = root.path("rules");
            List<Rule> parsed = new ArrayList<>();
            if (rulesNode.isArray()) {
                for (JsonNode r : rulesNode) {
                    String categoryName = r.path("category").asText();
                    IssueCategory category;
                    try {
                        category = IssueCategory.valueOf(categoryName);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Warning: custom rule references unknown category '"
                                + categoryName + "' — skipped.");
                        continue;
                    }
                    List<String> patterns = new ArrayList<>();
                    for (JsonNode p : r.path("patterns")) {
                        patterns.add(p.asText());
                    }
                    if (!patterns.isEmpty()) {
                        parsed.add(new Rule(category, patterns));
                    }
                }
            }
            return new CustomRules(parsed);
        } catch (IOException e) {
            System.err.println("Warning: could not read custom rules from " + path + ": " + e.getMessage());
            return empty();
        }
    }

    /** Returns the first matching category for the given log content, or null if no rule matches. */
    public IssueCategory match(String rawContent) {
        if (rules.isEmpty() || rawContent == null) return null;
        String lower = rawContent.toLowerCase();
        for (Rule rule : rules) {
            for (String pattern : rule.patterns()) {
                if (lower.contains(pattern.toLowerCase())) {
                    return rule.category();
                }
            }
        }
        return null;
    }

    public int size() {
        return rules.size();
    }

    public record Rule(IssueCategory category, List<String> patterns) {}
}
