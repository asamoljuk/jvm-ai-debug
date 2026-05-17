# CLI Reference

## Top-level usage

```
jvm-ai-debug [-hV] [COMMAND]
```

| Option | Description |
|--------|-------------|
| `-h`, `--help` | Print help and exit |
| `-V`, `--version` | Print version string and exit |

---

## Commands

### `analyze`

Reads an input file, detects the issue category, calls the configured AI provider, and prints the analysis.

```
jvm-ai-debug analyze [-hvV] [-f=<format>] [-p=<provider>] [-t=<type>] <inputFile>
```

**Positional argument**

| Argument | Description |
|----------|-------------|
| `<inputFile>` | Path to the file to analyze. Can be a stack trace, Maven/Gradle build log, test output, or any other JVM-related failure text. |

**Options**

| Option | Short | Values | Default | Description |
|--------|-------|--------|---------|-------------|
| `--provider` | `-p` | `openai` \| `anthropic` \| `ollama` \| `mock` | auto-detect | AI provider to use. Overrides the `JVM_AI_DEBUG_PROVIDER` environment variable and the auto-detection logic. |
| `--format` | `-f` | `text` \| `json` | `text` | Output format. `text` is human-readable; `json` is machine-readable and suitable for CI pipelines. |
| `--type` | `-t` | `stacktrace` \| `build-log` \| `test-failure` \| `auto` | `auto` | Input type hint. Currently accepted by the parser but detection is always automatic regardless of this value. Reserved for future use to allow type-specific parsing strategies. |
| `--verbose` | `-v` | — | false | Prints diagnostic information to **stderr**: provider name, input file, output format, detected category, confidence, and the full prompt that was sent to the AI provider. Normal analysis output still goes to **stdout**. |
| `--help` | `-h` | — | — | Print command-specific help and exit. |
| `--version` | `-V` | — | — | Print version and exit. |

**Exit codes**

| Code | Meaning |
|------|---------|
| `0` | Analysis completed successfully |
| `1` | Error — file not found, provider failure, or unhandled exception. Error message printed to stderr. |

**Examples**

```bash
# Basic analysis with mock provider
jvm-ai-debug analyze stacktrace.txt

# Specify provider explicitly
jvm-ai-debug analyze stacktrace.txt --provider openai
jvm-ai-debug analyze build.log --provider anthropic
jvm-ai-debug analyze failure.txt --provider ollama
jvm-ai-debug analyze stacktrace.txt --provider mock

# JSON output (useful for piping into jq or CI scripts)
jvm-ai-debug analyze stacktrace.txt --format json
jvm-ai-debug analyze stacktrace.txt --format json | jq '.suggestedFixes[]'

# Verbose mode — shows extracted evidence and full AI prompt on stderr
jvm-ai-debug analyze stacktrace.txt --verbose

# Combine options
jvm-ai-debug analyze build.log --provider mock --format json --verbose
```

**Redirecting output**

Normal output (the analysis) is written to **stdout**. Verbose diagnostics are written to **stderr**. This lets you redirect them independently:

```bash
# Save only the JSON analysis; verbose diagnostics still appear in the terminal
jvm-ai-debug analyze stacktrace.txt --format json --verbose > analysis.json

# Suppress verbose diagnostics entirely
jvm-ai-debug analyze stacktrace.txt --verbose 2>/dev/null

# Capture both
jvm-ai-debug analyze stacktrace.txt --verbose > analysis.txt 2> diagnostics.txt
```

---

### `version`

Prints the tool version and the Java runtime version.

```
jvm-ai-debug version
```

Example output:

```
AI JVM Debug Assistant v1.0.0
Java 17.0.12 (Oracle Corporation)
```

---

### `help`

Prints help for the top-level command or any subcommand.

```
jvm-ai-debug help
jvm-ai-debug help analyze
jvm-ai-debug analyze --help
```

---

## Environment variables

All environment variables are read at startup. They can be set in the shell, in a CI environment, or in a `.env` file sourced before invocation.

| Variable | Used by | Description |
|----------|---------|-------------|
| `JVM_AI_DEBUG_PROVIDER` | `AppConfig` | Sets the default provider. Overridden by `--provider` flag. Values: `openai`, `anthropic`, `ollama`, `mock`. |
| `OPENAI_API_KEY` | `AppConfig` | OpenAI API key. If set and no provider is specified, `openai` is auto-selected. Falls back to `mock` if the key is missing when `--provider openai` is used. |
| `ANTHROPIC_API_KEY` | `AppConfig` | Anthropic API key. If set and `OPENAI_API_KEY` is absent and no provider is specified, `anthropic` is auto-selected. Falls back to `mock` if the key is missing when `--provider anthropic` is used. |
| `OLLAMA_BASE_URL` | `AppConfig` | Ollama server base URL. Default: `http://127.0.0.1:11434`. Trailing slash is stripped automatically. |
| `OLLAMA_MODEL` | `AppConfig` | Ollama model name. Default: `llama3.1`. |

### Provider auto-detection order

When `--provider` is not specified, the provider is resolved in this exact order:

1. `JVM_AI_DEBUG_PROVIDER` environment variable (if set and non-blank)
2. `OPENAI_API_KEY` is present in the environment → use `openai`
3. `ANTHROPIC_API_KEY` is present in the environment → use `anthropic`
4. Default → `mock`

**Ollama is never auto-detected.** It must be opted into explicitly via `--provider ollama` or `JVM_AI_DEBUG_PROVIDER=ollama` because it has no API key to probe for. This is intentional — Ollama requires a running local server and should not be assumed available.

### Graceful key fallback

If a real provider is selected (either via flag or auto-detection) but its API key is not set, the tool prints a warning to stderr and falls back to the `mock` provider rather than exiting with an error:

```
OPENAI_API_KEY is not set. Falling back to mock provider.
```
