# AI Providers

The tool supports four providers through the `AiClient` interface. All providers receive the same structured prompt and are expected to return a JSON analysis response.

---

## Mock provider

**No API key. No network access. Zero configuration.**

The mock provider returns deterministic, hard-coded responses based solely on the detected issue category. It does not call any external service. This is the default when no API key is configured.

**When to use it:**
- Local development and testing
- CI environments without API key access
- Demonstrating the tool without credentials
- Verifying parsing and detection logic in isolation

**How to enable:**
```bash
jvm-ai-debug analyze stacktrace.txt --provider mock
# or
export JVM_AI_DEBUG_PROVIDER=mock
```

**Response characteristics by category:**

| Issue Category | Confidence | Notes |
|---------------|-----------|-------|
| `SPRING_CONTEXT_FAILURE` | HIGH | Uses detected class names from the log if available; falls back to `UserService`/`NotificationService` examples |
| `NULL_POINTER_EXCEPTION` | HIGH | |
| `CLASS_NOT_FOUND` | HIGH | |
| `NO_CLASS_DEF_FOUND` | HIGH | |
| `JUNIT_TEST_FAILURE` | HIGH | |
| `HIBERNATE_MAPPING_ERROR` | HIGH | |
| `JVM_MEMORY_ERROR` | HIGH | |
| `MAVEN_BUILD_FAILURE` | MEDIUM | |
| `GRADLE_BUILD_FAILURE` | MEDIUM | |
| `TESTNG_TEST_FAILURE` | MEDIUM | |
| `UNKNOWN` | LOW | Root cause text includes the exception names extracted from the log |

---

## OpenAI provider

Calls the [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat).

**Configuration:**

```bash
export OPENAI_API_KEY=sk-...
jvm-ai-debug analyze stacktrace.txt --provider openai
```

| Setting | Value |
|---------|-------|
| Endpoint | `https://api.openai.com/v1/chat/completions` |
| Default model | `gpt-4o-mini` |
| Temperature | `0.2` |
| Max tokens | `1500` |
| Connect timeout | 30 seconds |
| Read timeout | 60 seconds |
| Write timeout | 30 seconds |

**Request structure:**

```json
{
  "model": "gpt-4o-mini",
  "temperature": 0.2,
  "max_tokens": 1500,
  "messages": [
    {
      "role": "system",
      "content": "You are an expert Java/JVM debugging assistant. Respond only with valid JSON, no markdown code blocks."
    },
    {
      "role": "user",
      "content": "<structured prompt with evidence, raw excerpt, and output schema>"
    }
  ]
}
```

**Response parsing:**

The tool reads `choices[0].message.content` from the API response. If the model wraps the JSON in a markdown code fence (`` ```json ... ``` ``), it is stripped automatically before deserialization.

**Error handling:**

Non-2xx responses throw a `RuntimeException` with the HTTP status code and response body. The tool exits with code `1` and prints the error to stderr.

---

## Anthropic provider

Calls the [Anthropic Messages API](https://docs.anthropic.com/en/api/messages).

**Configuration:**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
jvm-ai-debug analyze stacktrace.txt --provider anthropic
```

| Setting | Value |
|---------|-------|
| Endpoint | `https://api.anthropic.com/v1/messages` |
| Default model | `claude-sonnet-4-6` |
| API version header | `anthropic-version: 2023-06-01` |
| Max tokens | `1500` |
| Connect timeout | 30 seconds |
| Read timeout | 60 seconds |
| Write timeout | 30 seconds |

**Request structure:**

```json
{
  "model": "claude-sonnet-4-6",
  "max_tokens": 1500,
  "system": "You are an expert Java/JVM debugging assistant. Respond only with valid JSON, no markdown code blocks.",
  "messages": [
    {
      "role": "user",
      "content": "<structured prompt with evidence, raw excerpt, and output schema>"
    }
  ]
}
```

**Response parsing:**

The tool reads `content[0].text` from the API response. Markdown code fences are stripped the same way as for OpenAI.

---

## Ollama provider

Calls a locally running [Ollama](https://ollama.com) server. No data leaves your machine. No API key required.

**Prerequisites:**

1. Install Ollama: https://ollama.com/download
2. Pull a model:
   ```bash
   ollama pull llama3.1
   ```
3. Start the server (runs in the background automatically after installation on most platforms):
   ```bash
   ollama serve
   ```

**Configuration:**

```bash
# Use defaults (http://127.0.0.1:11434, model llama3.1)
jvm-ai-debug analyze stacktrace.txt --provider ollama

# Custom model
export OLLAMA_MODEL=codellama
jvm-ai-debug analyze stacktrace.txt --provider ollama

# Remote Ollama server
export OLLAMA_BASE_URL=http://gpu-server:11434
export OLLAMA_MODEL=mistral
jvm-ai-debug analyze stacktrace.txt --provider ollama
```

| Setting | Default | Environment variable |
|---------|---------|---------------------|
| Base URL | `http://127.0.0.1:11434` | `OLLAMA_BASE_URL` |
| Model | `llama3.1` | `OLLAMA_MODEL` |
| Endpoint | `<base_url>/api/chat` | — |

| Timeout | Value |
|---------|-------|
| Connect | 10 seconds |
| Read | **5 minutes** |
| Write | 30 seconds |

The read timeout is deliberately 5 minutes. Local models — especially on CPU — can take a long time to generate a response. Adjust `OLLAMA_MODEL` to a smaller/faster model if latency is a concern.

**Request structure:**

```json
{
  "model": "llama3.1",
  "stream": false,
  "options": {
    "temperature": 0.2
  },
  "messages": [
    {
      "role": "system",
      "content": "You are an expert Java/JVM debugging assistant. Respond only with valid JSON, no markdown code blocks."
    },
    {
      "role": "user",
      "content": "<structured prompt>"
    }
  ]
}
```

**Note:** `stream` is set to `false`. The tool does not support streaming responses.

**Response parsing:**

The tool reads `message.content` from the Ollama response. Markdown code fences are stripped the same way as for OpenAI and Anthropic.

**Troubleshooting:**

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused` | Ollama server is not running | Run `ollama serve` |
| `404 Not Found` | Model not pulled | Run `ollama pull <model>` |
| Response timeout | Model is too large for available hardware | Use a smaller model (`ollama pull phi3`) |
| Garbled JSON in response | Model does not follow instructions well | Try a different model; `codellama` or `llama3.1` tend to produce clean JSON |

---

## Prompt structure (all real providers)

All real providers (OpenAI, Anthropic, Ollama) receive the same prompt built by `PromptBuilder`. The prompt contains:

1. A system instruction establishing the assistant role
2. The detected issue category and display name
3. Extracted evidence: exceptions, caused-by chain, class names, framework/build/test indicators
4. A raw excerpt of the input file (truncated to 2,500 characters)
5. An output schema that the model must follow

The output schema instructs the model to return a JSON object with these exact fields:

```json
{
  "detectedIssue": "<CATEGORY_NAME>",
  "title": "<short title>",
  "likelyRootCause": "<detailed explanation of the root cause>",
  "evidence": ["<evidence point 1>", "..."],
  "suggestedFixes": ["<fix 1>", "..."],
  "confidence": "HIGH|MEDIUM|LOW",
  "mentionedClasses": ["<ClassName1>", "..."]
}
```

If the model omits `detectedIssue` or `title`, the tool fills them in from the locally-detected category before formatting output.

---

## Adding a new provider

1. Create a class implementing `AiClient`:
   ```java
   public class MyProviderClient implements AiClient {
       @Override
       public AnalysisResponse analyze(AnalysisRequest request) { ... }
       @Override
       public String getProviderName() { return "myprovider"; }
   }
   ```
2. Add a case to `AppConfig.createAiClient()`:
   ```java
   case "myprovider" -> new MyProviderClient(...);
   ```
3. Update the `--provider` option description in `AnalyzeCommand`.
4. Use `OpenAiClient.extractJson(content)` (package-accessible static helper) to strip markdown fences from the LLM response before Jackson deserialization.
