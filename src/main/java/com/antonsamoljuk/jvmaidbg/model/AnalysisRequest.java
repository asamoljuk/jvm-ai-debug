package com.antonsamoljuk.jvmaidbg.model;

public class AnalysisRequest {

    private final DetectedIssue detectedIssue;
    private final String prompt;
    private final String rawContent;

    public AnalysisRequest(DetectedIssue detectedIssue, String prompt, String rawContent) {
        this.detectedIssue = detectedIssue;
        this.prompt = prompt;
        this.rawContent = rawContent;
    }

    public DetectedIssue getDetectedIssue() { return detectedIssue; }
    public String getPrompt() { return prompt; }
    public String getRawContent() { return rawContent; }
}
