package com.antonsamoljuk.jvmaidbg.output;

import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.antonsamoljuk.jvmaidbg.model.OutputFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;

public class OutputFormatter {

    private final ObjectMapper objectMapper;

    public OutputFormatter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String format(AnalysisResponse response, OutputFormat format) {
        return switch (format) {
            case JSON -> formatJson(response);
            case TEXT -> formatText(response);
        };
    }

    public String formatText(AnalysisResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AI JVM Debug Assistant ===\n\n");

        sb.append("Detected issue:\n");
        sb.append("  ").append(formatCategory(response.getDetectedIssue())).append("\n\n");

        sb.append("Likely root cause:\n");
        sb.append(wrapText(response.getLikelyRootCause(), 80, "  ")).append("\n\n");

        List<String> evidence = response.getEvidence();
        if (evidence != null && !evidence.isEmpty()) {
            sb.append("Evidence:\n");
            for (String point : evidence) {
                sb.append("  - ").append(point).append("\n");
            }
            sb.append("\n");
        }

        List<String> fixes = response.getSuggestedFixes();
        if (fixes != null && !fixes.isEmpty()) {
            sb.append("Suggested fixes:\n");
            for (int i = 0; i < fixes.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(fixes.get(i)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Confidence:\n");
        sb.append("  ").append(formatConfidence(response.getConfidence())).append("\n\n");

        List<String> classes = response.getMentionedClasses();
        if (classes != null && !classes.isEmpty()) {
            sb.append("Files/classes mentioned:\n");
            for (String cls : classes) {
                sb.append("  - ").append(cls).append("\n");
            }
        }

        return sb.toString().trim();
    }

    public String formatJson(AnalysisResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response to JSON", e);
        }
    }

    private String formatCategory(String category) {
        if (category == null) return "Unknown";
        String spaced = category.replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : spaced.toCharArray()) {
            if (c == ' ') {
                sb.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String formatConfidence(String confidence) {
        if (confidence == null) return "Unknown";
        return switch (confidence.toUpperCase()) {
            case "HIGH" -> "High";
            case "MEDIUM" -> "Medium";
            case "LOW" -> "Low";
            default -> confidence;
        };
    }

    private String wrapText(String text, int width, String indent) {
        if (text == null) return indent + "(no information)";
        StringBuilder sb = new StringBuilder();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder(indent);
        for (String word : words) {
            if (line.length() + word.length() + 1 > width) {
                sb.append(line).append("\n");
                line = new StringBuilder(indent).append(word);
            } else {
                if (line.length() > indent.length()) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > indent.length()) sb.append(line);
        return sb.toString();
    }
}
