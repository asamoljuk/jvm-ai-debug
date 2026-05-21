package com.antonsamoljuk.jvmaidbg.cli;

import com.antonsamoljuk.jvmaidbg.ai.AiClient;
import com.antonsamoljuk.jvmaidbg.ai.CachingAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OllamaAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OllamaLauncher;
import com.antonsamoljuk.jvmaidbg.ai.TokenUsage;
import com.antonsamoljuk.jvmaidbg.analysis.AnalysisService;
import com.antonsamoljuk.jvmaidbg.analysis.BatchCorrelator;
import com.antonsamoljuk.jvmaidbg.analysis.CustomRules;
import com.antonsamoljuk.jvmaidbg.analysis.PromptBuilder;
import com.antonsamoljuk.jvmaidbg.config.AppConfig;
import com.antonsamoljuk.jvmaidbg.config.AppSettings;
import com.antonsamoljuk.jvmaidbg.config.ConfigLoader;
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

    @Option(names = {"--watch", "-w"},
            description = "Tail the given file and analyze each new stack trace as it appears. "
                    + "Requires exactly one positional file. Implies --no-cache. Ctrl-C to exit.")
    private boolean watch;

    @Override
    public Integer call() {
        OutputFormat outputFormat = parseFormat(format);

        List<Path> files = collectInputFiles();
        if (files.isEmpty()) {
            System.err.println("Error: no input files. Provide a file path or use --dir <directory>.");
            return 2;
        }

        if (watch && files.size() != 1) {
            System.err.println("Error: --watch requires exactly one file (got " + files.size() + ").");
            return 2;
        }

        AppSettings settings = ConfigLoader.load();
        AppConfig config = new AppConfig();
        AiClient rawClient = config.createAiClient(provider, settings);

        // Cache: CLI --no-cache or --watch wins; otherwise config file's cacheEnabled wins; default true.
        boolean cacheOn = !noCache && !watch && (settings.cacheEnabled == null || settings.cacheEnabled);
        AiClient aiClient = cacheOn ? new CachingAiClient(rawClient) : rawClient;

        Path effectiveRulesFile = rulesFile != null ? rulesFile
                : (settings.rulesFile != null ? Path.of(settings.rulesFile) : CustomRules.DEFAULT_PATH);
        CustomRules customRules = CustomRules.load(effectiveRulesFile);

        int maxChars = settings.maxPromptChars != null ? settings.maxPromptChars : PromptBuilder.DEFAULT_MAX_PROMPT_CHARS;
        AnalysisService service = new AnalysisService(aiClient, customRules, maxChars);
        OutputFormatter formatter = new OutputFormatter();

        InputType inputType = parseInputType(type);

        if (verbose) {
            System.err.println("[verbose] Provider: " + aiClient.getProviderName());
            System.err.println("[verbose] Files: " + files.size());
            System.err.println("[verbose] Output format: " + outputFormat);
            System.err.println("[verbose] Input type: " + inputType);
            System.err.println("[verbose] Cache: " + (cacheOn ? "enabled" : "disabled"));
            System.err.println("[verbose] Custom rules: " + customRules.size());
            System.err.println("[verbose] Max prompt chars: " + maxChars);
            List<Path> sources = ConfigLoader.loadedSources();
            System.err.println("[verbose] Config sources: " + (sources.isEmpty() ? "(none, using defaults)" : sources));
        }

        if (rawClient instanceof OllamaAiClient ollamaClient) {
            try {
                OllamaLauncher.ensureRunning(ollamaClient.getBaseUrl());
            } catch (RuntimeException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        if (watch) {
            return runWatch(files.get(0), service, formatter, outputFormat, inputType);
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
    private static final long WATCH_DEBOUNCE_MS = 2_000;

    private Integer runWatch(Path file, AnalysisService service, OutputFormatter formatter,
                             OutputFormat outputFormat, InputType inputType) {
        if (!Files.isRegularFile(file)) {
            System.err.println("Error: --watch target is not a regular file: " + file);
            return 2;
        }
        System.err.println("Watching " + file + " — Ctrl-C to exit. Triggering analysis on new stack traces.");

        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            main.interrupt();
            System.err.println();
            System.err.println("Watch stopped.");
        }));

        LogTailer tailer = new LogTailer(file, WATCH_DEBOUNCE_MS, chunk -> {
            String timestamp = java.time.LocalTime.now().withNano(0).toString();
            System.out.println();
            System.out.println("=== " + timestamp + " — new stack trace detected ===");
            try {
                AnalysisService.AnalysisResult result = service.analyzeContent(chunk, inputType);
                System.out.println(formatter.format(result.response(), outputFormat));
                if (result.usage() != null) {
                    System.err.println("Tokens: " + result.usage().formatShort() + " [" + result.usage().model() + "]");
                }
            } catch (Exception e) {
                System.err.println("Error analyzing chunk: " + e.getMessage());
            }
        });

        try {
            tailer.tail();
            return 0;
        } catch (InterruptedException e) {
            return 0;
        } catch (IOException e) {
            System.err.println("Error tailing " + file + ": " + e.getMessage());
            return 1;
        }
    }

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

            if (result.usage() != null) {
                System.err.println("Tokens: " + result.usage().formatShort()
                        + " [" + result.usage().model() + "]");
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

        ArrayNode results = mapper.createArrayNode();
        for (FileOutcome o : outcomes) {
            ObjectNode node = mapper.createObjectNode();
            node.put("file", o.file.toString());
            if (o.error != null) {
                node.put("error", o.error);
            } else {
                node.set("analysis", mapper.valueToTree(o.result.response()));
                if (o.result.usage() != null) {
                    node.set("usage", mapper.valueToTree(o.result.usage()));
                }
            }
            results.add(node);
        }

        // Wrap results in a top-level object with summary + correlations for direct CI consumption.
        ObjectNode root = mapper.createObjectNode();
        ObjectNode summary = root.putObject("summary");
        summary.put("total", outcomes.size());
        summary.put("high", (int) outcomes.stream().filter(o -> "HIGH".equalsIgnoreCase(o.confidence)).count());
        summary.put("errors", (int) outcomes.stream().filter(o -> o.error != null).count());

        ArrayNode correlations = root.putArray("correlations");
        for (BatchCorrelator.Cluster c : correlate(outcomes)) {
            ObjectNode cn = correlations.addObject();
            cn.put("category", c.category().name());
            if (c.topException() != null) cn.put("topException", c.topException());
            cn.put("fileCount", c.fileCount());
            ArrayNode files = cn.putArray("files");
            c.files().forEach(files::add);
        }

        root.set("results", results);

        try {
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
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

        TokenUsage total = outcomes.stream()
                .filter(o -> o.result != null && o.result.usage() != null)
                .map(o -> o.result.usage())
                .reduce(TokenUsage.zero(), TokenUsage::plus);
        if (total.inputTokens() > 0 || total.outputTokens() > 0) {
            System.out.println("Total tokens: " + total.formatShort() + " [" + total.model() + "]");
        }

        List<BatchCorrelator.Cluster> clusters = correlate(outcomes);
        if (!clusters.isEmpty()) {
            System.out.println();
            System.out.println("Correlations:");
            for (BatchCorrelator.Cluster c : clusters) {
                System.out.println("  " + c.fileCount() + " of " + outcomes.size()
                        + " files show " + c.signature() + " — likely a single root cause.");
            }
        }
    }

    private List<BatchCorrelator.Cluster> correlate(List<FileOutcome> outcomes) {
        List<BatchCorrelator.Entry> entries = new ArrayList<>();
        for (FileOutcome o : outcomes) {
            if (o.result == null) continue;
            List<String> exceptions = o.result.detectedIssue().getEvidence().getExceptionNames();
            String top = (exceptions != null && !exceptions.isEmpty()) ? exceptions.get(0) : null;
            entries.add(new BatchCorrelator.Entry(
                    o.file.getFileName().toString(),
                    o.result.detectedIssue().getCategory(),
                    top));
        }
        return BatchCorrelator.correlate(entries);
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
