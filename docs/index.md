# AI JVM Debug Assistant — Documentation

AI-powered CLI tool for analyzing JVM stack traces, build logs, and test failures. Paste in a failure; get a structured root-cause explanation and actionable fixes.

---

## Contents

| Document | What it covers |
|----------|---------------|
| [Getting Started](getting-started.md) | Installation, build, first run in under two minutes |
| [CLI Reference](usage.md) | Every command, option, flag, and environment variable |
| [AI Providers](providers.md) | mock, OpenAI, Anthropic, Ollama — configuration and trade-offs |
| [Issue Categories](issue-categories.md) | All 11 detectable issue types, detection signals, and example output |
| [Output Formats](output-formats.md) | Text and JSON output with annotated examples |
| [CI Integration](ci-integration.md) | GitHub Actions workflow and PR comment bot |
| [Architecture](architecture.md) | Internal design, data flow, and extension guide |

---

## Quick example

```bash
# Build
mvn clean package -DskipTests

# Analyze (no API key needed)
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --provider mock
```

Output:

```
=== AI JVM Debug Assistant ===

Detected issue:
  Spring Context Failure

Likely root cause:
  A circular dependency between Spring beans prevents the application
  context from initializing...

Suggested fixes:
  1. Refactor the dependency direction...
  2. Introduce an interface boundary...

Confidence:
  High
```
