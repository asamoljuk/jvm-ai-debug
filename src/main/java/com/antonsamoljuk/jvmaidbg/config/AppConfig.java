package com.antonsamoljuk.jvmaidbg.config;

import com.antonsamoljuk.jvmaidbg.ai.AiClient;
import com.antonsamoljuk.jvmaidbg.ai.AnthropicAiClient;
import com.antonsamoljuk.jvmaidbg.ai.MockAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OllamaAiClient;
import com.antonsamoljuk.jvmaidbg.ai.OpenAiClient;

public class AppConfig {

    public static final String ENV_OPENAI_API_KEY    = "OPENAI_API_KEY";
    public static final String ENV_OPENAI_MODEL      = "OPENAI_MODEL";
    public static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    public static final String ENV_ANTHROPIC_MODEL   = "ANTHROPIC_MODEL";
    public static final String ENV_PROVIDER          = "JVM_AI_DEBUG_PROVIDER";
    public static final String ENV_OLLAMA_BASE_URL   = "OLLAMA_BASE_URL";
    public static final String ENV_OLLAMA_MODEL      = "OLLAMA_MODEL";

    public AiClient createAiClient(String providerOverride) {
        return createAiClient(providerOverride, AppSettings.empty());
    }

    public AiClient createAiClient(String providerOverride, AppSettings settings) {
        String provider = resolveProvider(providerOverride, settings);
        return switch (provider.toLowerCase()) {
            case "openai"    -> createOpenAiClient(settings);
            case "anthropic" -> createAnthropicClient(settings);
            case "ollama"    -> createOllamaClient(settings);
            case "mock"      -> new MockAiClient();
            default -> {
                System.err.println("Unknown provider '" + provider + "', falling back to mock.");
                yield new MockAiClient();
            }
        };
    }

    private String resolveProvider(String override, AppSettings settings) {
        // Precedence: CLI flag > env var > config file > auto-detect from API keys > mock
        if (override != null && !override.isBlank()) return override;
        String env = System.getenv(ENV_PROVIDER);
        if (env != null && !env.isBlank()) return env;
        if (settings.defaultProvider != null && !settings.defaultProvider.isBlank()) return settings.defaultProvider;
        if (System.getenv(ENV_OPENAI_API_KEY) != null) return "openai";
        if (System.getenv(ENV_ANTHROPIC_API_KEY) != null) return "anthropic";
        System.err.println("Warning: no provider specified and no API key found in environment. "
                + "Using mock provider — output is deterministic placeholder text, not real AI analysis. "
                + "Set OPENAI_API_KEY, ANTHROPIC_API_KEY, or pass --provider ollama to enable real analysis.");
        return "mock";
    }

    private AiClient createOpenAiClient(AppSettings settings) {
        String key = System.getenv(ENV_OPENAI_API_KEY);
        if (key == null || key.isBlank()) {
            System.err.println("OPENAI_API_KEY is not set. Falling back to mock provider.");
            return new MockAiClient();
        }
        String model = firstNonBlank(System.getenv(ENV_OPENAI_MODEL), settings.openaiModel);
        return (model != null) ? new OpenAiClient(key, model) : new OpenAiClient(key);
    }

    private AiClient createAnthropicClient(AppSettings settings) {
        String key = System.getenv(ENV_ANTHROPIC_API_KEY);
        if (key == null || key.isBlank()) {
            System.err.println("ANTHROPIC_API_KEY is not set. Falling back to mock provider.");
            return new MockAiClient();
        }
        String model = firstNonBlank(System.getenv(ENV_ANTHROPIC_MODEL), settings.anthropicModel);
        return (model != null) ? new AnthropicAiClient(key, model) : new AnthropicAiClient(key);
    }

    private AiClient createOllamaClient(AppSettings settings) {
        String baseUrl = firstNonBlank(System.getenv(ENV_OLLAMA_BASE_URL), settings.ollamaBaseUrl);
        String model   = firstNonBlank(System.getenv(ENV_OLLAMA_MODEL),    settings.ollamaModel);
        return new OllamaAiClient(baseUrl, model);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
