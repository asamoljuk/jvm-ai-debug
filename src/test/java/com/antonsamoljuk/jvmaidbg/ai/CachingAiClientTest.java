package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.antonsamoljuk.jvmaidbg.model.DetectedIssue;
import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;
import com.antonsamoljuk.jvmaidbg.model.IssueCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CachingAiClientTest {

    @Test
    void firstCallDelegatesAndCaches(@TempDir Path tempDir) {
        CountingClient counter = new CountingClient();
        CachingAiClient cache = new CachingAiClient(counter, tempDir);
        AnalysisRequest req = sampleRequest("some-content");

        AnalysisResponse first = cache.analyze(req);
        assertEquals("counted", first.getDetectedIssue());
        assertEquals(1, counter.callCount);
    }

    @Test
    void secondCallWithSameContentHitsCache(@TempDir Path tempDir) {
        CountingClient counter = new CountingClient();
        CachingAiClient cache = new CachingAiClient(counter, tempDir);
        AnalysisRequest req = sampleRequest("identical-content");

        cache.analyze(req);
        AnalysisResponse second = cache.analyze(req);

        assertEquals("counted", second.getDetectedIssue());
        assertEquals(1, counter.callCount, "delegate should only be called once");
    }

    @Test
    void differentContentMissesCache(@TempDir Path tempDir) {
        CountingClient counter = new CountingClient();
        CachingAiClient cache = new CachingAiClient(counter, tempDir);

        cache.analyze(sampleRequest("content-a"));
        cache.analyze(sampleRequest("content-b"));

        assertEquals(2, counter.callCount);
    }

    @Test
    void cacheKeyIsStableAndHex() {
        String key = CachingAiClient.cacheKey("hello", "mock");
        assertEquals(64, key.length(), "SHA-256 hex is 64 chars");
        assertTrue(key.matches("[0-9a-f]+"));
        assertEquals(key, CachingAiClient.cacheKey("hello", "mock"), "deterministic");
    }

    @Test
    void cacheKeyDiffersByProvider() {
        assertNotEquals(
                CachingAiClient.cacheKey("same-content", "openai"),
                CachingAiClient.cacheKey("same-content", "ollama"));
    }

    private static AnalysisRequest sampleRequest(String rawContent) {
        DetectedIssue detected = new DetectedIssue(IssueCategory.UNKNOWN, "MEDIUM", new ExtractedEvidence());
        return new AnalysisRequest(detected, "prompt-text", rawContent);
    }

    private static class CountingClient implements AiClient {
        int callCount = 0;

        @Override
        public AnalysisResponse analyze(AnalysisRequest request) {
            callCount++;
            AnalysisResponse r = new AnalysisResponse();
            r.setDetectedIssue("counted");
            r.setTitle("counted");
            return r;
        }

        @Override
        public String getProviderName() {
            return "counter";
        }
    }
}
