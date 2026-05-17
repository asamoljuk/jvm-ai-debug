# Architecture

## Package structure

```
src/main/java/com/antonsamoljuk/jvmaidbg/
├── cli/           Entry point and picocli commands
├── parser/        Raw text extraction
├── analysis/      Detection, prompt construction, orchestration
├── ai/            Provider interface and implementations
├── output/        Response formatting
├── config/        Environment-based provider wiring
└── model/         Data classes shared across packages
```

---

## Data flow

Every analysis runs through a linear pipeline. Each stage has a single responsibility and produces the input for the next.

```
File on disk
     │
     ▼
AnalysisService.analyze(Path)
     │  reads file content
     ▼
LogParser.parse(String)
     │  returns ExtractedEvidence
     ▼
IssueDetector.detect(ExtractedEvidence, String)
     │  returns DetectedIssue (category + confidence + evidence)
     ▼
PromptBuilder.build(DetectedIssue, String)
     │  returns AnalysisRequest (DetectedIssue + prompt string + raw content)
     ▼
AiClient.analyze(AnalysisRequest)
     │  returns AnalysisResponse
     ▼
OutputFormatter.format(AnalysisResponse, OutputFormat)
     │  returns formatted String
     ▼
System.out
```

`AnalysisService` owns the pipeline end-to-end. The CLI (`AnalyzeCommand`) instantiates `AppConfig`, gets an `AiClient`, constructs `AnalysisService`, and calls `analyze()`. It does not touch the parser, detector, or prompt builder directly.

---

## Components

### `CliApplication`
Picocli entry point. Registers three subcommands: `AnalyzeCommand`, `VersionCommand`, and picocli's built-in `HelpCommand`. Invokes `System.exit()` with picocli's exit code.

### `AnalyzeCommand`
Handles the `analyze` subcommand. Reads CLI arguments, calls `AppConfig.createAiClient()`, constructs `AnalysisService`, runs the analysis, formats the result with `OutputFormatter`, and prints to stdout. Verbose diagnostics go to stderr. Returns exit code `0` on success, `1` on any exception.

### `VersionCommand`
Prints `"AI JVM Debug Assistant v1.0.0"` and the Java runtime version from `System.getProperty("java.version")`.

### `LogParser`
Stateless. Applies four compiled regex patterns to extract:

| What | Pattern |
|------|---------|
| Exception/Error names | `([a-zA-Z][a-zA-Z0-9_.]*(?:Exception\|Error))` — simplified to the simple class name |
| Caused-by lines | `^\s*Caused by:\s*(.+)` (multiline) |
| Stack frame classes and methods | `\s+at\s+([a-zA-Z][a-zA-Z0-9_.]+)\.([a-zA-Z][a-zA-Z0-9_]+)\(` |
| Application class names | uppercase-initial tokens ending in known suffixes (Service, Controller, Repository, Bean, Factory, Manager, Handler, Listener, Config, Configuration, Application, Component, Exception, Error, Impl) |

Framework, build tool, and test framework detection uses simple `String.contains()` checks on a lowercased copy of the content.

The raw excerpt stored in `ExtractedEvidence` is truncated to **3,000 characters**. The excerpt passed to the prompt is separately truncated to **2,500 characters** by `PromptBuilder`.

### `IssueDetector`
Stateless. Maps `ExtractedEvidence` + raw content to one `IssueCategory`. Uses priority-ordered if-else rules (see [Issue Categories](issue-categories.md) for the exact order and signals). Returns a `DetectedIssue` containing the category, a confidence string (`HIGH`/`MEDIUM`/`LOW`), and the original evidence.

Confidence rules:
- `HIGH` — exceptions list is non-empty **and** caused-by chain is non-empty
- `MEDIUM` — only one of the two is non-empty
- `LOW` — both are empty, or category is `UNKNOWN`

### `PromptBuilder`
Stateless. Builds a structured natural-language prompt that includes:
1. Role instruction
2. Detected category name and display name
3. All non-empty evidence lists from `ExtractedEvidence`
4. Raw log excerpt (truncated to 2,500 chars)
5. Output schema the model must follow

Returns an `AnalysisRequest` record bundling the `DetectedIssue`, prompt string, and raw content.

### `AiClient` (interface)
Two methods: `AnalysisResponse analyze(AnalysisRequest)` and `String getProviderName()`.

### `MockAiClient`
Returns hard-coded `AnalysisResponse` objects keyed on `IssueCategory`. No network calls. For `SPRING_CONTEXT_FAILURE`, uses the detected class names from the input if available, otherwise falls back to `UserService`/`NotificationService` example names.

### `OpenAiClient`
Calls `https://api.openai.com/v1/chat/completions` with model `gpt-4o-mini`, temperature `0.2`, max_tokens `1500`. Reads `choices[0].message.content` from the response. Uses the static `extractJson()` helper to strip markdown code fences before deserializing with Jackson.

### `AnthropicAiClient`
Calls `https://api.anthropic.com/v1/messages` with model `claude-sonnet-4-6`, max_tokens `1500`, and the `anthropic-version: 2023-06-01` header. Reads `content[0].text` from the response. Reuses `OpenAiClient.extractJson()` for fence stripping.

### `OllamaAiClient`
Calls `<baseUrl>/api/chat` with `stream: false` and temperature `0.2`. Reads `message.content` from the response. Uses a 5-minute read timeout to accommodate slow local models. Reuses `OpenAiClient.extractJson()` for fence stripping.

### `OpenAiClient.extractJson(String)` (package-level static)
Shared by all real providers. Strips `` ```json ... ``` `` (or `` ``` ... ``` ``) markdown fences if present, then extracts the substring between the first `{` and the last `}`.

### `AnalysisService`
Owns the analysis pipeline. Validates file existence before reading. After the AI call, fills in `detectedIssue` and `title` fields on the response if the AI left them blank, using the locally-detected category as a fallback. Returns an `AnalysisResult` record containing all pipeline artefacts.

### `OutputFormatter`
Stateless (Jackson `ObjectMapper` is instantiated once and reused). Two public methods:
- `formatText(AnalysisResponse)` — word-wraps the root cause at 80 chars, numbers the fixes, omits sections with no data
- `formatJson(AnalysisResponse)` — pretty-printed JSON via Jackson with `INDENT_OUTPUT`

Category names are formatted to Title Case by replacing underscores with spaces and capitalizing each word.

### `AppConfig`
Reads environment variables and creates the appropriate `AiClient`. Provider resolution order: explicit flag → `JVM_AI_DEBUG_PROVIDER` → `OPENAI_API_KEY` present → `ANTHROPIC_API_KEY` present → mock. Falls back to `MockAiClient` with a warning if a real provider is selected but its key is missing.

---

## Model classes

| Class | Type | Role |
|-------|------|------|
| `IssueCategory` | Enum | 11 detectable categories, each with a display name string |
| `OutputFormat` | Enum | `TEXT`, `JSON` |
| `ExtractedEvidence` | Mutable POJO | Holds all signals extracted by `LogParser`. Built incrementally — mutable by design. |
| `DetectedIssue` | Immutable class | Category + confidence + reference to `ExtractedEvidence` |
| `AnalysisRequest` | Immutable class | `DetectedIssue` + prompt string + raw content |
| `AnalysisResponse` | Mutable POJO with Jackson annotations | Bidirectional: deserialized from LLM JSON responses **and** serialized for `--format json` output. `@JsonIgnoreProperties(ignoreUnknown = true)` prevents deserialization failures if the LLM adds extra fields. |

---

## Dependencies

| Library | Version | Used for |
|---------|---------|---------|
| picocli | 4.7.5 | CLI argument parsing and help generation |
| picocli-codegen | 4.7.5 | Annotation processor — generates native-image reflection config |
| Jackson Databind | 2.17.0 | JSON serialization/deserialization |
| OkHttp | 4.12.0 | HTTP client for OpenAI, Anthropic, Ollama API calls |
| JUnit Jupiter | 5.10.2 | Test framework |
| Mockito Core | 5.10.0 | Test mocking (available but not used in current tests) |
| Mockito JUnit Jupiter | 5.10.0 | JUnit 5 extension for Mockito |

The fat JAR is built with `maven-shade-plugin` 3.5.2, producing `target/jvm-ai-debug.jar`. The original thin JAR `target/jvm-ai-debug-1.0.0-SNAPSHOT.jar` is also produced but not used.

---

## Testing

Tests live in `src/test/java/com/antonsamoljuk/jvmaidbg/`. Sample input files are in `src/test/resources/samples/`.

| Test class | Covers |
|-----------|--------|
| `LogParserTest` | Exception extraction, caused-by chain, class name extraction, framework/build/test detection, full parse, excerpt truncation |
| `IssueDetectorTest` | All main categories, confidence scoring, UNKNOWN fallback |
| `PromptBuilderTest` | Category name in prompt, raw content in prompt, output schema in prompt, evidence in prompt, raw content preservation, truncation |
| `MockAiClientTest` | All 11 categories return valid non-null responses, parametrized; HIGH/MEDIUM/LOW confidence; detected class names used when available |
| `OutputFormatterTest` | Header, all text sections, JSON validity, JSON field presence, JSON array structure, null-safe handling |
| `OllamaAiClientTest` | Provider name, null/blank defaults, trailing slash stripping, custom URL/model, `AppConfig` wiring |

No tests make real network calls. All AI client tests use the mock provider or verify configuration only.

---

## Extension points

### New issue category
1. Add enum value to `IssueCategory`
2. Add detection rule to `IssueDetector.categorize()` — place it at the correct priority position
3. Add a mock response in `MockAiClient`
4. Add a sample input in `src/test/resources/samples/` and cover it in `IssueDetectorTest`

### New AI provider
1. Implement `AiClient`
2. Add a case to `AppConfig.createAiClient()`
3. Use `OpenAiClient.extractJson()` to handle markdown-fenced JSON from the model
4. Update the `--provider` description in `AnalyzeCommand`

### New output format
1. Add a value to `OutputFormat`
2. Add a case to `OutputFormatter.format()`
3. Update the `--format` description in `AnalyzeCommand`
