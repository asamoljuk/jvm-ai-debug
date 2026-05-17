package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.DetectedIssue;
import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;

import java.util.List;

public class PromptBuilder {

    private static final String OUTPUT_SCHEMA = """
            {
              "detectedIssue": "<CATEGORY_NAME>",
              "title": "<short title>",
              "likelyRootCause": "<detailed explanation of the root cause>",
              "evidence": ["<evidence point 1>", "<evidence point 2>"],
              "suggestedFixes": ["<fix 1>", "<fix 2>"],
              "confidence": "HIGH|MEDIUM|LOW",
              "mentionedClasses": ["<ClassName1>", "<ClassName2>"]
            }
            All array values must be quoted strings. Do not use ellipsis (...) or any placeholder tokens.""";

    public AnalysisRequest build(DetectedIssue detectedIssue, String rawContent) {
        String prompt = buildPrompt(detectedIssue, rawContent);
        return new AnalysisRequest(detectedIssue, prompt, rawContent);
    }

    private String buildPrompt(DetectedIssue detectedIssue, String rawContent) {
        ExtractedEvidence ev = detectedIssue.getEvidence();
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert Java/JVM debugging assistant. Analyze the following JVM failure and provide a structured diagnosis.\n\n");

        sb.append("## Detected Issue Category\n");
        sb.append(detectedIssue.getCategory().name()).append(" — ").append(detectedIssue.getTitle()).append("\n\n");

        sb.append("## Extracted Evidence\n");
        appendList(sb, "Exceptions found", ev.getExceptionNames());
        appendList(sb, "Caused-by chain", ev.getCausedByChain());
        appendList(sb, "Mentioned classes", ev.getClassNames());
        appendList(sb, "Framework indicators", ev.getFrameworkIndicators());
        appendList(sb, "Build tool indicators", ev.getBuildToolIndicators());
        appendList(sb, "Test framework indicators", ev.getTestFrameworkIndicators());

        sb.append("\n## Raw Log Excerpt\n```\n");
        sb.append(truncate(rawContent, 2500));
        sb.append("\n```\n\n");

        sb.append("## Instructions\n");
        sb.append("Respond ONLY with a valid JSON object matching this schema. Do not wrap in markdown code blocks.\n");
        sb.append("Focus on actionable, specific advice for a Java/Spring developer.\n\n");
        sb.append(OUTPUT_SCHEMA);

        return sb.toString();
    }

    private void appendList(StringBuilder sb, String label, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append("- ").append(label).append(": ");
        sb.append(String.join(", ", items)).append("\n");
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "\n... [truncated]";
    }
}
