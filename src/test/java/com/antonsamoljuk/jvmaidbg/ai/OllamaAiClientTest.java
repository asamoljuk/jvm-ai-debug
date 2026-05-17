package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OllamaAiClientTest {

    @Test
    void providerNameIsOllama() {
        OllamaAiClient client = new OllamaAiClient();
        assertEquals("ollama", client.getProviderName());
    }

    @Test
    void defaultsApplyWhenConstructorArgsAreNull() {
        OllamaAiClient client = new OllamaAiClient(null, null);
        assertEquals(OllamaAiClient.DEFAULT_BASE_URL, client.getBaseUrl());
        assertEquals(OllamaAiClient.DEFAULT_MODEL, client.getModel());
    }

    @Test
    void defaultsApplyWhenConstructorArgsAreBlank() {
        OllamaAiClient client = new OllamaAiClient("", "  ");
        assertEquals(OllamaAiClient.DEFAULT_BASE_URL, client.getBaseUrl());
        assertEquals(OllamaAiClient.DEFAULT_MODEL, client.getModel());
    }

    @Test
    void trailingSlashIsStrippedFromBaseUrl() {
        OllamaAiClient client = new OllamaAiClient("http://custom-host:11434/", "codellama");
        assertEquals("http://custom-host:11434", client.getBaseUrl());
        assertEquals("codellama", client.getModel());
    }

    @Test
    void customBaseUrlAndModelArePreserved() {
        OllamaAiClient client = new OllamaAiClient("http://10.0.0.5:11434", "mistral");
        assertEquals("http://10.0.0.5:11434", client.getBaseUrl());
        assertEquals("mistral", client.getModel());
    }

    @Test
    void appConfigCreatesOllamaClientWhenProviderIsOllama() {
        AppConfig config = new AppConfig();
        AiClient client = config.createAiClient("ollama");

        assertInstanceOf(OllamaAiClient.class, client);
        assertEquals("ollama", client.getProviderName());
    }
}
