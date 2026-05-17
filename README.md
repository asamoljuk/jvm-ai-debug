# AI JVM Debug Assistant

> AI-powered CLI tool for analyzing JVM stack traces, CI failures, and Java build logs.

[![Build & Test](https://github.com/asamoljuk/jvm-ai-debug/actions/workflows/build.yml/badge.svg)](https://github.com/antonsamoljuk/jvm-ai-debug/actions/workflows/build.yml)

---

## Why this project exists

Every Java developer has stared at a wall of red text — a BeanCreationException, an NPE in production, a Maven build failing in CI with a cryptic `cannot find symbol`. Finding the root cause means knowing where to look, what the framework is doing, and what patterns the error matches.

This tool closes the feedback loop between a failed build and a fix. It:

- **Parses** the raw log automatically — no copy-pasting into ChatGPT
- **Detects** the issue category (Spring context failure, NPE, Hibernate mapping, etc.)
- **Explains** the likely root cause in plain English
- **Suggests** concrete, ordered fix steps
- **Integrates** with CI/CD — JSON output can be parsed by downstream tooling

---

## Features

- JVM stack trace analysis with exception extraction and caused-by chain parsing
- Spring Boot circular dependency and context failure detection
- Maven and Gradle build log failure diagnosis
- JUnit / TestNG test failure analysis
- Hibernate / JPA mapping error detection
- JVM memory and stack overflow analysis
- AI-assisted root cause explanation via OpenAI, Claude, or local Ollama
- Provider abstraction — swap AI backends without changing the tool
- Mock provider for zero-config local demo (no API key needed)
- Ollama support for fully-local, zero-cost analysis (no data leaves your machine)
- JSON output for CI pipeline integration
- Verbose mode for debugging the analysis pipeline

---

## Installation

**Requirements:** Java 21, Maven 3.9+

```bash
git clone https://github.com/asamoljuk/jvm-ai-debug.git
cd jvm-ai-debug
mvn clean package -DskipTests
```

This produces `target/jvm-ai-debug.jar`.

Add a shell alias for convenience:

```bash
alias jvm-ai-debug='java -jar /path/to/jvm-ai-debug.jar'
```

---

## Usage

```
jvm-ai-debug analyze <file> [options]
jvm-ai-debug version
jvm-ai-debug help
```

### Options

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--provider`, `-p` | `openai`, `anthropic`, `ollama`, `mock` | auto-detect | AI provider |
| `--format`, `-f` | `text`, `json` | `text` | Output format |
| `--type`, `-t` | `stacktrace`, `build-log`, `test-failure`, `auto` | `auto` | Input type hint |
| `--verbose`, `-v` | — | false | Show diagnostic details and AI prompt |

### Environment variables

| Variable | Description |
|----------|-------------|
| `OPENAI_API_KEY` | OpenAI API key |
| `ANTHROPIC_API_KEY` | Anthropic / Claude API key |
| `JVM_AI_DEBUG_PROVIDER` | Default provider (`openai`, `anthropic`, `ollama`, `mock`) |
| `OLLAMA_BASE_URL` | Ollama server URL (default: `http://localhost:11434`) |
| `OLLAMA_MODEL` | Ollama model name (default: `llama3.1`) |

---

## Example commands

```bash
# Mock provider (no API key needed)
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --provider mock

# Auto-detect provider from environment
export OPENAI_API_KEY=sk-...
java -jar target/jvm-ai-debug.jar analyze build.log

# Anthropic Claude provider
export ANTHROPIC_API_KEY=sk-ant-...
java -jar target/jvm-ai-debug.jar analyze failure.txt --provider anthropic

# Local Ollama provider (no API key, no data leaves your machine)
# Prerequisite: `ollama pull llama3.1` and have `ollama serve` running
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --provider ollama

# Ollama with a custom model and remote host
export OLLAMA_BASE_URL=http://gpu-box:11434
export OLLAMA_MODEL=codellama
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --provider ollama

# JSON output for CI integration
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --format json

# Build log analysis
java -jar target/jvm-ai-debug.jar analyze build.log --type build-log

# Verbose: shows extracted evidence and the AI prompt
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --verbose
```

---

## Example input/output

**Input** (`spring-bean-failure.txt`):
```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'userService'
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'notificationService'
Caused by: java.lang.IllegalStateException: Circular dependency detected
```

**Output** (`--format text`):
```
=== AI JVM Debug Assistant ===

Detected issue:
  Spring Context Failure

Likely root cause:
  A circular dependency between Spring beans prevents the application context
  from initializing. Spring cannot resolve the dependency graph because two
  or more beans mutually depend on each other through constructor injection.

Evidence:
  - BeanCreationException found in stack trace
  - UnsatisfiedDependencyException indicates unresolved bean dependency
  - Circular dependency detected in the Spring dependency graph
  - Constructor injection used — Spring cannot break the cycle automatically

Suggested fixes:
  1. Refactor the dependency direction: extract shared logic into a third service
  2. Introduce an interface boundary to decouple the beans
  3. Use setter or field injection with @Lazy on one side as a temporary workaround
  4. Split responsibilities: the circular dependency often signals a design smell
  5. Use ApplicationContext.getBean() lazily within a method if restructuring is not feasible

Confidence:
  High

Files/classes mentioned:
  - UserService
  - NotificationService
```

**Output** (`--format json`):
```json
{
  "detectedIssue" : "SPRING_CONTEXT_FAILURE",
  "title" : "Spring application context failed to start",
  "likelyRootCause" : "A circular dependency between Spring beans...",
  "evidence" : [ "BeanCreationException found in stack trace", "..." ],
  "suggestedFixes" : [ "Refactor the dependency direction", "..." ],
  "confidence" : "HIGH",
  "mentionedClasses" : [ "UserService", "NotificationService" ]
}
```

---

## Architecture overview

```
src/main/java/com/antonsamoljuk/jvmaidbg/
├── cli/
│   ├── CliApplication.java       # picocli entry point
│   ├── AnalyzeCommand.java       # analyze subcommand
│   └── VersionCommand.java       # version subcommand
├── parser/
│   └── LogParser.java            # extracts exceptions, caused-by, class names
├── analysis/
│   ├── IssueDetector.java        # maps evidence → IssueCategory
│   ├── PromptBuilder.java        # builds structured LLM prompt
│   └── AnalysisService.java      # orchestrates the full pipeline
├── ai/
│   ├── AiClient.java             # provider interface
│   ├── MockAiClient.java         # deterministic offline responses
│   ├── OpenAiClient.java         # OpenAI Chat Completions
│   ├── AnthropicAiClient.java    # Anthropic Messages API
│   └── OllamaAiClient.java       # Local Ollama (/api/chat)
├── output/
│   └── OutputFormatter.java      # text and JSON rendering
├── config/
│   └── AppConfig.java            # reads env vars, creates AiClient
└── model/
    ├── IssueCategory.java
    ├── OutputFormat.java
    ├── ExtractedEvidence.java
    ├── DetectedIssue.java
    ├── AnalysisRequest.java
    └── AnalysisResponse.java
```

### Data flow

```
File → LogParser → ExtractedEvidence
                         ↓
                   IssueDetector → DetectedIssue
                         ↓
                   PromptBuilder → AnalysisRequest
                         ↓
                     AiClient → AnalysisResponse
                         ↓
                  OutputFormatter → stdout
```

---

## Running tests

```bash
mvn test
```

Sample inputs live in `src/test/resources/samples/`.

---

## Roadmap

- **IntelliJ IDEA plugin** — right-click a stack trace → analyze inline
- **Gradle/Maven plugin** — auto-analyze build failures as part of the lifecycle
- **OpenTelemetry / JFR support** — analyze JVM flight recorder recordings
- **Web UI dashboard** — team-level view of recurring failure patterns

---

## Why this is useful for engineering teams

| Problem | How this tool helps |
|---------|---------------------|
| Junior devs blocked on cryptic Spring errors | Get an explanation in seconds, not after 30 min of Googling |
| CI failures with no clear owner | JSON output enables automated triage and routing |
| Long feedback loops in large codebases | Pipe build logs directly — no manual copy-paste |
| On-call fatigue from JVM OOM errors | Specific, actionable fix suggestions for memory issues |
| Onboarding new engineers | Learn JVM/Spring error patterns from explanations |

---

## License

MIT
