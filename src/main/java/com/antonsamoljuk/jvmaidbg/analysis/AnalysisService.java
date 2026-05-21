package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.ai.AiClient;
import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.antonsamoljuk.jvmaidbg.model.DetectedIssue;
import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;
import com.antonsamoljuk.jvmaidbg.model.InputType;
import com.antonsamoljuk.jvmaidbg.parser.LogParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AnalysisService {

    private final LogParser logParser;
    private final IssueDetector issueDetector;
    private final PromptBuilder promptBuilder;
    private final AiClient aiClient;

    public AnalysisService(AiClient aiClient) {
        this(aiClient, CustomRules.empty());
    }

    public AnalysisService(AiClient aiClient, CustomRules customRules) {
        this.logParser = new LogParser();
        this.issueDetector = new IssueDetector(customRules);
        this.promptBuilder = new PromptBuilder();
        this.aiClient = aiClient;
    }

    public AnalysisResult analyze(Path inputFile) throws IOException {
        return analyze(inputFile, InputType.AUTO);
    }

    public AnalysisResult analyze(Path inputFile, InputType inputType) throws IOException {
        if (!Files.exists(inputFile)) {
            throw new IOException("File not found: " + inputFile);
        }
        String content = Files.readString(inputFile);
        return analyzeContent(content, inputType);
    }

    public AnalysisResult analyzeContent(String content) {
        return analyzeContent(content, InputType.AUTO);
    }

    public AnalysisResult analyzeContent(String content, InputType inputType) {
        ExtractedEvidence evidence = logParser.parse(content);
        DetectedIssue detectedIssue = issueDetector.detect(evidence, content, inputType);
        AnalysisRequest request = promptBuilder.build(detectedIssue, content);
        AnalysisResponse response = aiClient.analyze(request);

        // Ensure detected issue is set if AI didn't fill it
        if (response.getDetectedIssue() == null || response.getDetectedIssue().isBlank()) {
            response.setDetectedIssue(detectedIssue.getCategory().name());
        }
        if (response.getTitle() == null || response.getTitle().isBlank()) {
            response.setTitle(detectedIssue.getTitle());
        }

        return new AnalysisResult(detectedIssue, request, response, aiClient.getProviderName());
    }

    public record AnalysisResult(
            DetectedIssue detectedIssue,
            AnalysisRequest request,
            AnalysisResponse response,
            String providerName
    ) {}
}
