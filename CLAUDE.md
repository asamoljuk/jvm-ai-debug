# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Full build + tests (the standard verify command)
mvn clean verify

# Build the fat JAR only (skips tests)
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=LogParserTest

# Run a single test method
mvn test -Dtest=IssueDetectorTest#detectsSpringContextFailure

# Run the CLI locally after building
java -jar target/jvm-ai-debug.jar analyze <file> --provider mock
java -jar target/jvm-ai-debug.jar analyze <file> --provider mock --format json
java -jar target/jvm-ai-debug.jar version
```

## Architecture

The pipeline is a linear chain of five stages — each stage produces the input for the next:

```
LogParser → IssueDetector → PromptBuilder → AiClient → OutputFormatter
```

`AnalysisService` owns this chain end-to-end. The CLI (`AnalyzeCommand`) calls only `AnalysisService`; it never touches parsing or AI directly.

### Adding a new issue category

1. Add the enum value to `IssueCategory`.
2. Add detection logic to `IssueDetector.categorize()` — note that **ordering matters**: JUnit/TestNG must stay above Maven/Gradle, and Spring/memory checks must stay above the generic exception checks.
3. Add a deterministic mock response in `MockAiClient` (new `case` in the switch).
4. Add a sample input to `src/test/resources/samples/` and cover it in `IssueDetectorTest`.

### Adding a new AI provider

1. Implement `AiClient` (`analyze()` + `getProviderName()`).
2. Wire it in `AppConfig.createAiClient()`.
3. Real providers must handle LLM responses that may contain markdown-fenced JSON — use `OpenAiClient.extractJson()` (static helper) to strip code fences before Jackson deserialization.

### Key design decisions

- **Mock provider is always available** — `MockAiClient` returns deterministic responses keyed on `IssueCategory` with no network call. This is the default when no API key is set.
- **Provider auto-detection order**: explicit `--provider` flag → `JVM_AI_DEBUG_PROVIDER` env var → `OPENAI_API_KEY` present → `ANTHROPIC_API_KEY` present → mock. **Ollama is never auto-detected** — it must be opted into explicitly (`--provider ollama` or `JVM_AI_DEBUG_PROVIDER=ollama`) because there's no API key sentinel to probe for. Configure via `OLLAMA_BASE_URL` (default `http://127.0.0.1:11434`) and `OLLAMA_MODEL` (default `llama3.1`).
- **`AnalysisResponse` is Jackson-bidirectional** — it is both serialized to JSON for `--format json` output and deserialized from LLM responses. Keep `@JsonIgnoreProperties(ignoreUnknown = true)` on it.
- **Source/target is Java 17** locally (machine has JDK 17); CI runs Java 21 via Temurin. Features used are compatible with both (records, switch expressions, text blocks).

## CI / PR Bot

The GitHub Actions workflow (`.github/workflows/build.yml`) runs `mvn package -DskipTests` first to produce the tool JAR, then runs `mvn verify` with output piped to `build-output.txt`. On failure it runs `jvm-ai-debug analyze build-output.txt --provider mock --format json` and posts the result as a structured PR comment via `actions/github-script`. The job still fails after posting the comment.

## Conversation Style

**Language:** Respond primarily in English. Use Russian for clarifications on complex or ambiguous concepts when it helps understanding (e.g., a brief Russian note alongside a technical explanation).

**Response detail:** Default to moderate — a brief explanation of what changed and why it matters. If asked to go deeper ("explain in detail", "walk me through this"), switch to a full detailed walkthrough.

**Clarifying questions:** Ask before starting if there is genuine ambiguity or missing information that would affect the approach. Do not ask for the sake of it — make reasonable assumptions for straightforward requests and state what was assumed.

**Multiple valid approaches:**
- Small or low-risk changes (single file, easily reversible): pick the best option and implement it directly.
- Architectural or cross-cutting decisions (new provider, pipeline change, schema change): present 2–3 options with short tradeoffs and wait for confirmation before proceeding.

## Sample Inputs

`src/test/resources/samples/` contains four representative inputs (`spring-bean-failure.txt`, `null-pointer.txt`, `maven-build-failure.txt`, `junit-failure.txt`). These are used by unit tests and are also useful for manual CLI testing.
