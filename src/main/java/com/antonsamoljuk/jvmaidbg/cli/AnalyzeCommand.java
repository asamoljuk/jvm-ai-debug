package com.antonsamoljuk.jvmaidbg.cli;

import com.antonsamoljuk.jvmaidbg.ai.AiClient;
import com.antonsamoljuk.jvmaidbg.ai.CachingAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OllamaAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OllamaLauncher;
import com.antonsamoljuk.jvmaidbg.analysis.AnalysisService;
import com.antonsamoljuk.jvmaidbg.analysis.CustomRules;
import com.antonsamoljuk.jvmaidbg.config.AppConfig;
import com.antonsamoljuk.jvmaidbg.model.InputType;
import com.antonsamoljuk.jvmaidbg.model.OutputFormat;
import com.antonsamoljuk.jvmaidbg.output.OutputFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(
        name = "analyze",
        description = "Analyze a JVM stack trace, build log, or test failure file (or many at once)",
        mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    @Parameters(arity = "0..*", description = "Input file(s) to analyze. Combine with --dir to add a whole directory.")
    private List<Path> inputFiles;

    @Option(names = "--dir",
            description = "Analyze every regular file in this directory (added to any files passed positionally)")
    private Path inputDir;

    @Option(names = {"--provider", "-p"},
            description = "AI provider: openai | anthropic | ollama | mock (default: auto-detect from env)")
    private String provider;

    @Option(names = {"--format", "-f"},
            description = "Output format: text | json (default: text)",
            defaultValue = "text")
    private String format;

    @Option(names = {"--type", "-t"},
            description = "Input type hint: stacktrace | build-log | test-failure | auto (default: auto). "
                    + "Biases detection: build-log skips test-framework checks; "
                    + "test-failure skips build-tool checks; stacktrace skips both.",
            defaultValue = "auto")
    private String type;

    @Option(names = {"--verbose", "-v"},
            description = "Show additional diagnostic details including the AI prompt")
    private boolean verbose;

    @Option(names = {"--no-cache"},
            description = "Bypass the response cache and force a fresh AI call")
    private boolean noCache;

    @Option(names = {"--rules"},
            description = "Path to a custom rules JSON file (default: ~/.jvm-ai-debug/rules.json if it exists)")
    private Path rulesFile;

    @Override
    public Integer call() {
        OutputFormat outputFormat = parseFormat(format);

        List<Path> files = collectInputFiles();
        if (files.isEmpty()) {
            System.err.println("Error: no input files. Provide a file path or use --dir <directory>.");
            return 2;
        }

        AppConfig config = new AppConfig();
        AiClient rawClient = config.createAiClient(provider);
        AiClient aiClient = noCache ? rawClient : new CachingAiClient(rawClient);
        CustomRules customRules = CustomRules.load(rulesFile != null ? rulesFile : CustomRules.DEFAULT_PATH);
        AnalysisService service = new AnalysisService(aiClient, customRules);
        OutputFormatter formatter = new OutputFormatter();

        InputType inputType = parseInputType(type);

        if (verbose) {
            System.err.println("[verbose] Provider: " + aiClient.getProviderName());
            System.err.println("[verbose] Files: " + files.size());
            System.err.println("[verbose] Output format: " + outputFormat);
            System.err.println("[verbose] Input type: " + inputType);
            System.err.println("[verbose] Cache: " + (noCache ? "disabled" : "enabled"));
            System.err.println("[verbose] Custom rules: " + customRules.size());
        }

        if (rawClient instanceof OllamaAiClient ollamaClient) {
            try {
                OllamaLauncher.ensureRunning(ollamaClient.getBaseUrl());
            } catch (RuntimeException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        List<FileOutcome> outcomes = new ArrayList<>();
        for (Path file : files) {
            outcomes.add(analyzeOne(file, service, formatter, outputFormat, inputType, files.size() > 1));
        }

        if (outputFormat == OutputFormat.JSON && files.size() > 1) {
            emitJsonArray(outcomes);
        }
        if (outputFormat == OutputFormat.TEXT && files.size() > 1) {
            emitSummary(outcomes);
        }

        long highCount = outcomes.stream().filter(o -> "HIGH".equalsIgnoreCase(o.confidence)).count();
        long failures = outcomes.stream().filter(o -> o.error != null).count();
        if (failures > 0) return 1;
        // Exit code = number of HIGH-confidence findings (capped at 125 to stay within POSIX range)
        return (int) Math.min(highCount, 125);
    }

    private static final Path STDIN_SENTINEL = Path.of("-");

    private FileOutcome analyzeOne(Path file, AnalysisService service, OutputFormatter formatter,
                                   OutputFormat outputFormat, InputType inputType, boolean batch) {
        boolean isStdin = STDIN_SENTINEL.equals(file);
        String label = isStdin ? "<stdin>" : file.getFileName().toString();
        if (batch && outputFormat == OutputFormat.TEXT) {
            System.out.println();
            System.out.println("=== " + label + " ===");
        }
        try {
            AnalysisService.AnalysisResult result;
            try (Spinner spinner = new Spinner("Analyzing " + label)) {
                if (isStdin) {
                    try (InputStream in = System.in) {
                        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        result = service.analyzeContent(content, inputType);
                    }
                } else {
                    result = service.analyze(file, inputType);
                }
            }

            if (verbose) {
                System.err.println("[verbose] " + file + " → " + result.detectedIssue().getCategory()
                        + " (" + result.detectedIssue().getConfidence() + ")");
            }

            if (outputFormat == OutputFormat.JSON && !batch) {
                System.out.println(formatter.format(result.response(), outputFormat));
            } else if (outputFormat == OutputFormat.TEXT) {
                System.out.println(formatter.format(result.response(), outputFormat));
            }
            return new FileOutcome(file, result, null);
        } catch (Exception e) {
            System.err.println("Error analyzing " + label + ": " + e.getMessage());
            if (verbose) e.printStackTrace(System.err);
            return new FileOutcome(file, null, e.getMessage());
        }
    }

    private List<Path> collectInputFiles() {
        List<Path> files = new ArrayList<>();
        if (inputFiles != null) files.addAll(inputFiles);
        if (inputDir != null) {
            if (!Files.isDirectory(inputDir)) {
                System.err.println("Warning: --dir " + inputDir + " is not a directory, ignoring.");
            } else {
                try (Stream<Path> stream = Files.list(inputDir)) {
                    stream.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::getFileName))
                            .forEach(files::add);
                } catch (IOException e) {
                    System.err.println("Warning: could not list " + inputDir + ": " + e.getMessage());
                }
            }
        }
        return files;
    }

    private void emitJsonArray(List<FileOutcome> outcomes) {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ArrayNode arr = mapper.createArrayNode();
        for (FileOutcome o : outcomes) {
            ObjectNode node = mapper.createObjectNode();
            node.put("file", o.file.toString());
            if (o.error != null) {
                node.put("error", o.error);
            } else {
                node.set("analysis", mapper.valueToTree(o.result.response()));
            }
            arr.add(node);
        }
        try {
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
        } catch (IOException e) {
            System.err.println("Failed to serialize batch JSON: " + e.getMessage());
        }
    }

    private void emitSummary(List<FileOutcome> outcomes) {
        System.out.println();
        System.out.println("=== Summary ===");
        System.out.printf("%-40s %-25s %s%n", "File", "Category", "Confidence");
        System.out.println("-".repeat(80));
        for (FileOutcome o : outcomes) {
            String name = o.file.getFileName().toString();
            if (name.length() > 38) name = name.substring(0, 35) + "...";
            if (o.error != null) {
                System.out.printf("%-40s %-25s %s%n", name, "ERROR", o.error);
            } else {
                System.out.printf("%-40s %-25s %s%n",
                        name,
                        o.result.detectedIssue().getCategory(),
                        o.result.detectedIssue().getConfidence());
            }
        }
        long high = outcomes.stream().filter(o -> "HIGH".equalsIgnoreCase(o.confidence)).count();
        long errors = outcomes.stream().filter(o -> o.error != null).count();
        System.out.println();
        System.out.println(outcomes.size() + " file(s) analyzed — " + high + " HIGH, " + errors + " error(s).");
    }

    private OutputFormat parseFormat(String fmt) {
        if (fmt == null) return OutputFormat.TEXT;
        return switch (fmt.toLowerCase()) {
            case "json" -> OutputFormat.JSON;
            default -> OutputFormat.TEXT;
        };
    }

    private InputType parseInputType(String t) {
        if (t == null) return InputType.AUTO;
        return switch (t.toLowerCase()) {
            case "stacktrace"    -> InputType.STACKTRACE;
            case "build-log"     -> InputType.BUILD_LOG;
            case "test-failure"  -> InputType.TEST_FAILURE;
            default              -> InputType.AUTO;
        };
    }

    private static final class FileOutcome {
        final Path file;
        final AnalysisService.AnalysisResult result;
        final String error;
        final String confidence;

        FileOutcome(Path file, AnalysisService.AnalysisResult result, String error) {
            this.file = file;
            this.result = result;
            this.error = error;
            this.confidence = result != null ? result.detectedIssue().getConfidence() : null;
        }
    }
}
