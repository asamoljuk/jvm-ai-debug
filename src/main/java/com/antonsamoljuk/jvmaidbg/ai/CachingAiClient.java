package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Decorator that caches AI responses on disk keyed by SHA-256(rawContent + provider).
 * Identical re-runs become near-instant and cost nothing. Cache lives at
 * ~/.jvm-ai-debug/cache/&lt;hash&gt;.json.
 */
public class CachingAiClient implements AiClient {

    private final AiClient delegate;
    private final Path cacheDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public CachingAiClient(AiClient delegate) {
        this(delegate, defaultCacheDir());
    }

    public CachingAiClient(AiClient delegate, Path cacheDir) {
        this.delegate = delegate;
        this.cacheDir = cacheDir;
    }

    @Override
    public AnalysisResponse analyze(AnalysisRequest request) {
        String key = cacheKey(request.getRawContent(), delegate.getProviderName());
        Path cacheFile = cacheDir.resolve(key + ".json");

        if (Files.exists(cacheFile)) {
            try {
                AnalysisResponse cached = mapper.readValue(cacheFile.toFile(), AnalysisResponse.class);
                System.err.println("Using cached analysis (" + key.substring(0, 12) + "). Pass --no-cache to force re-analysis.");
                return cached;
            } catch (IOException e) {
                // Cache file corrupt — fall through to fresh analysis and overwrite.
            }
        }

        AnalysisResponse response = delegate.analyze(request);
        try {
            Files.createDirectories(cacheDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), response);
        } catch (IOException e) {
            System.err.println("Warning: could not write cache file " + cacheFile + ": " + e.getMessage());
        }
        return response;
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    static String cacheKey(String content, String providerName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((content + ":" + providerName).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Path defaultCacheDir() {
        return Path.of(System.getProperty("user.home"), ".jvm-ai-debug", "cache");
    }
}
