package com.antonsamoljuk.jvmaidbg.config;

import com.antonsamoljuk.jvmaidbg.ai.AiClient;
import com.antonsamoljuk.jvmaidbg.ai.AnthropicAiClient;
import com.antonsamoljuk.jvmaidbg.ai.MockAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OllamaAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OpenAiClient;

public class AppConfig {

    public static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    public static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    public static final String ENV_PROVIDER = "JVM_AI_DEBUG_PROVIDER";
    public static final String ENV_OLLAMA_BASE_URL = "OLLAMA_BASE_URL";
    public static final String ENV_OLLAMA_MODEL = "OLLAMA_MODEL";

    public AiClient createAiClient(String providerOverride) {
        String provider = resolveProvider(providerOverride);
        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiClient();
            case "anthropic" -> createAnthropicClient();
            case "ollama" -> createOllamaClient();
            case "mock" -> new MockAiClient();
            default -> {
                System.err.println("Unknown provider '" + provider + "', falling back to mock.");
                yield new MockAiClient();
            }
        };
    }

    private String resolveProvider(String override) {
        if (override != null && !override.isBlank()) return override;
        String env = System.getenv(ENV_PROVIDER);
        if (env != null && !env.isBlank()) return env;
        // Auto-detect based on available keys (Ollama is local — must be opted into explicitly)
        if (System.getenv(ENV_OPENAI_API_KEY) != null) return "openai";
        if (System.getenv(ENV_ANTHROPIC_API_KEY) != null) return "anthropic";
        return "mock";
    }

    private AiClient createOpenAiClient() {
        String key = System.getenv(ENV_OPENAI_API_KEY);
        if (key == null || key.isBlank()) {
            System.err.println("OPENAI_API_KEY is not set. Falling back to mock provider.");
            return new MockAiClient();
        }
        return new OpenAiClient(key);
    }

    private AiClient createAnthropicClient() {
        String key = System.getenv(ENV_ANTHROPIC_API_KEY);
        if (key == null || key.isBlank()) {
            System.err.println("ANTHROPIC_API_KEY is not set. Falling back to mock provider.");
            return new MockAiClient();
        }
        return new AnthropicAiClient(key);
    }

    private AiClient createOllamaClient() {
        String baseUrl = System.getenv(ENV_OLLAMA_BASE_URL);
        String model = System.getenv(ENV_OLLAMA_MODEL);
        return new OllamaAiClient(baseUrl, model);
    }
}
