package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AnthropicAiClient implements AiClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final String API_VERSION = "2023-06-01";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicAiClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public AnthropicAiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AnalysisResponse analyze(AnalysisRequest request) {
        try {
            String requestBody = buildRequestBody(request.getPrompt());
            Request httpRequest = new Request.Builder()
                    .url(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
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

    private String buildRequestBody(String prompt) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 1500);

        ObjectNode systemNode = root.putObject("system");
        // Anthropic uses top-level system field (string)
        root.put("system", "You are an expert Java/JVM debugging assistant. Respond only with valid JSON, no markdown code blocks.");

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        return objectMapper.writeValueAsString(root);
    }

    private AnalysisResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("content").get(0).path("text").asText();
        String json = OpenAiClient.extractJson(content);
        return objectMapper.readValue(json, AnalysisResponse.class);
    }
}
