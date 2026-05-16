package com.antonsamoljuk.jvmaidbg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResponse {

    @JsonProperty("detectedIssue")
    private String detectedIssue;

    @JsonProperty("title")
    private String title;

    @JsonProperty("likelyRootCause")
    private String likelyRootCause;

    @JsonProperty("evidence")
    private List<String> evidence;

    @JsonProperty("suggestedFixes")
    private List<String> suggestedFixes;

    @JsonProperty("confidence")
    private String confidence;

    @JsonProperty("mentionedClasses")
    private List<String> mentionedClasses;

    public AnalysisResponse() {}

    public AnalysisResponse(String detectedIssue, String title, String likelyRootCause,
                            List<String> evidence, List<String> suggestedFixes,
                            String confidence, List<String> mentionedClasses) {
        this.detectedIssue = detectedIssue;
        this.title = title;
        this.likelyRootCause = likelyRootCause;
        this.evidence = evidence;
        this.suggestedFixes = suggestedFixes;
        this.confidence = confidence;
        this.mentionedClasses = mentionedClasses;
    }

    public String getDetectedIssue() { return detectedIssue; }
    public void setDetectedIssue(String detectedIssue) { this.detectedIssue = detectedIssue; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLikelyRootCause() { return likelyRootCause; }
    public void setLikelyRootCause(String likelyRootCause) { this.likelyRootCause = likelyRootCause; }

    public List<String> getEvidence() { return evidence; }
    public void setEvidence(List<String> evidence) { this.evidence = evidence; }

    public List<String> getSuggestedFixes() { return suggestedFixes; }
    public void setSuggestedFixes(List<String> suggestedFixes) { this.suggestedFixes = suggestedFixes; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public List<String> getMentionedClasses() { return mentionedClasses; }
    public void setMentionedClasses(List<String> mentionedClasses) { this.mentionedClasses = mentionedClasses; }
}
