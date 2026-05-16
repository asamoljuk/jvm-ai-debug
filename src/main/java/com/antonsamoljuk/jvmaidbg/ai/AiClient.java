package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;

public interface AiClient {

    AnalysisResponse analyze(AnalysisRequest request);

    String getProviderName();
}
