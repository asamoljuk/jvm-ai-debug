package com.antonsamoljuk.jvmaidbg.model;

public class DetectedIssue {

    private final IssueCategory category;
    private final String title;
    private final String confidence;
    private final ExtractedEvidence evidence;

    public DetectedIssue(IssueCategory category, String confidence, ExtractedEvidence evidence) {
        this.category = category;
        this.title = category.getDisplayName();
        this.confidence = confidence;
        this.evidence = evidence;
    }

    public IssueCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getConfidence() { return confidence; }
    public ExtractedEvidence getEvidence() { return evidence; }
}
