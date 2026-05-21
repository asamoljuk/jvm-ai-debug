package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;

public class OpenAiClient implements AiClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public OpenAiClient(String apiKey, String model) {
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
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, AiClientDefaults.JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "(no body)";
                    throw new RuntimeException("OpenAI API error " + response.code() + ": " + body);
                }
                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call OpenAI API: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    private String buildRequestBody(String prompt) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.2);
        root.put("max_tokens", 1500);

        ArrayNode messages = root.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", AiClientDefaults.SYSTEM_PROMPT);

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        return objectMapper.writeValueAsString(root);
    }

    private AnalysisResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        return parseAnalysisJson(content);
    }

    private AnalysisResponse parseAnalysisJson(String content) throws IOException {
        String json = extractJson(content);
        return objectMapper.readValue(json, AnalysisResponse.class);
    }

    static String extractJson(String text) {
        if (text == null || text.isBlank()) return "{}";
        String trimmed = text.trim();
        // Strip markdown code block if present
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        // Find first { to last }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }
        return sanitizeLlmJson(trimmed);
    }

    // Strips unquoted ... and trailing commas — artifacts local models emit that Jackson rejects.
    static String sanitizeLlmJson(String json) {
        String s = json.replace("…", "..."); // normalize Unicode ellipsis → ASCII before regex
        s = s.replaceAll(",\\s*\\.{2,}\\s*(?=[,\\]])", ""); // ["a", ...] → ["a"]
        s = s.replaceAll("(?<=\\[)\\s*\\.{2,}\\s*,\\s*", "");         // [..., "a"] → ["a"]
        s = s.replaceAll("\\[\\s*\\.{2,}\\s*\\]", "[]");              // [...]      → []
        s = s.replaceAll(",\\s*([}\\]])", "$1");                       // {"k":"v",} → {"k":"v"}
        return s;
    }
}
