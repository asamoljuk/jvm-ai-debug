package com.antonsamoljuk.jvmaidbg.ai;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Shared constants and HTTP client factories for AI providers.
 * Centralizing these here means timeouts and the system prompt are tuned in one place.
 */
public final class AiClientDefaults {

    public static final String SYSTEM_PROMPT =
            "You are an expert Java/JVM debugging assistant. Respond only with valid JSON, no markdown code blocks.";

    public static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private AiClientDefaults() {}

    /** Cloud APIs (OpenAI, Anthropic) — fast networks, capped response time. */
    public static OkHttpClient cloudHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /** Local models (Ollama) — instant connect but inference can be slow. */
    public static OkHttpClient localHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
}
