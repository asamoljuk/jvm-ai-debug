package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.Optional;

public class AnthropicAiClient implements AiClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile TokenUsage lastUsage;

    public AnthropicAiClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public AnthropicAiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = AiClientDefaults.cloudHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AnalysisResponse analyze(AnalysisRequest request) {
        try {
            String requestBody = buildRequestBody(request.getPrompt());
            Request httpRequest = new Request.Builder()
                    .url(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", AiClientDefaults.ANTHROPIC_API_VERSION)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, AiClientDefaults.JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "(no body)";
                    throw new RuntimeException("Anthropic API error " + response.code() + ": " + body);
                }
                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Anthropic API: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public Optional<TokenUsage> getLastUsage() {
        return Optional.ofNullable(lastUsage);
    }

    private String buildRequestBody(String prompt) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 1500);

        // Anthropic uses a top-level "system" string field, not a message role.
        root.put("system", AiClientDefaults.SYSTEM_PROMPT);

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        return objectMapper.writeValueAsString(root);
    }

    private AnalysisResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("content").get(0).path("text").asText();
        // Capture token usage if present (Anthropic: usage.input_tokens / output_tokens)
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            int in  = usage.path("input_tokens").asInt(0);
            int out = usage.path("output_tokens").asInt(0);
            lastUsage = new TokenUsage(model, in, out, Pricing.estimateUsd(model, in, out));
        }
        String json = OpenAiClient.extractJson(content);
        return objectMapper.readValue(json, AnalysisResponse.class);
    }
}
