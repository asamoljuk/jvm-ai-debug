package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;

import java.util.Optional;

public interface AiClient {

    AnalysisResponse analyze(AnalysisRequest request);

    String getProviderName();

    /**
     * Token usage from the most recent {@link #analyze} call, or empty if the provider
     * does not report usage (Mock, Ollama, or a cache hit).
     */
    default Optional<TokenUsage> getLastUsage() {
        return Optional.empty();
    }
}
