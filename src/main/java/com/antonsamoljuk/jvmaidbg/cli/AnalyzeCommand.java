package com.antonsamoljuk.jvmaidbg.cli;

import com.antonsamoljuk.jvmaidbg.ai.AiClient;
import com.antonsamoljuk.jvmaidbg.analysis.AnalysisService;
import com.antonsamoljuk.jvmaidbg.config.AppConfig;
import com.antonsamoljuk.jvmaidbg.model.OutputFormat;
import com.antonsamoljuk.jvmaidbg.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "analyze",
        description = "Analyze a JVM stack trace, build log, or test failure file",
        mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file to analyze (stack trace, build log, test output)")
    private Path inputFile;

    @Option(names = {"--provider", "-p"},
            description = "AI provider: openai | anthropic | ollama | mock (default: auto-detect from env)")
    private String provider;

    @Option(names = {"--format", "-f"},
            description = "Output format: text | json (default: text)",
            defaultValue = "text")
    private String format;

    @Option(names = {"--type", "-t"},
            description = "Input type hint: stacktrace | build-log | test-failure | auto (default: auto)",
            defaultValue = "auto")
    private String type;

    @Option(names = {"--verbose", "-v"},
            description = "Show additional diagnostic details including the AI prompt")
    private boolean verbose;

    @Override
    public Integer call() {
        OutputFormat outputFormat = parseFormat(format);
        AppConfig config = new AppConfig();
        AiClient aiClient = config.createAiClient(provider);
        AnalysisService service = new AnalysisService(aiClient);
        OutputFormatter formatter = new OutputFormatter();

        if (verbose) {
            System.err.println("[verbose] Provider: " + aiClient.getProviderName());
            System.err.println("[verbose] Input file: " + inputFile);
            System.err.println("[verbose] Output format: " + outputFormat);
        }

        try {
            AnalysisService.AnalysisResult result = service.analyze(inputFile);

            if (verbose) {
                System.err.println("[verbose] Detected category: " + result.detectedIssue().getCategory());
                System.err.println("[verbose] Confidence: " + result.detectedIssue().getConfidence());
                System.err.println("\n[verbose] Prompt sent to AI:\n---");
                System.err.println(result.request().getPrompt());
                System.err.println("---\n");
            }

            System.out.println(formatter.format(result.response(), outputFormat));
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    private OutputFormat parseFormat(String fmt) {
        if (fmt == null) return OutputFormat.TEXT;
        return switch (fmt.toLowerCase()) {
            case "json" -> OutputFormat.JSON;
            default -> OutputFormat.TEXT;
        };
    }
}
